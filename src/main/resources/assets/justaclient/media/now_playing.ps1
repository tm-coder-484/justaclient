# Justaclient now-playing helper for Windows. Reads the system media session (the same one the volume flyout shows)
# through WinRT and prints one JSON line about twice a second. Media keys arrive as a word in $CmdFile.
param([string]$ArtDir, [string]$CmdFile, [string]$Only, [int]$ParentPid)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
	$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

function Await($op, [Type]$type) {
	$task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
	if (-not $task.Wait(3000)) { throw 'WinRT call timed out' }
	return $task.Result
}

function Say($obj) {
	[Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress))
	[Console]::Out.Flush()
}

$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]
$playingStatus = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing
$pausedStatus = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Paused

try {
	$manager = Await ($managerType::RequestAsync()) $managerType
} catch {
	Say @{ status = 'unavailable'; message = 'Media sessions are not available on this Windows version' }
	exit 1
}

# The Spotify web player plays inside a browser, so its media session belongs to the browser, not to Spotify. A
# browser session counts as Spotify when a browser window title mentions Spotify or shows the web player's
# "Track * Artist" title, or failing that when it carries an album (YouTube and most video sites leave it empty).
$browserPattern = 'chrome|msedge|firefox|brave|opera|vivaldi|chromium|TheBrowserCompany|librewolf|floorp|zen|waterfox|yandex'
$bullet = [string][char]0x2022
$browserTitles = @()
$titlesAt = [DateTime]::MinValue

function BrowserTitles {
	if (([DateTime]::Now - $script:titlesAt).TotalSeconds -ge 2) {
		$script:titlesAt = [DateTime]::Now
		$script:browserTitles = @(Get-Process -ErrorAction SilentlyContinue | Where-Object {
			$_.ProcessName -match $browserPattern -and $_.MainWindowTitle
		} | ForEach-Object { $_.MainWindowTitle })
	}
	return $script:browserTitles
}

function FindSpotifyWeb($sessions) {
	$fallback = $null
	foreach ($s in $sessions) {
		if ($s.SourceAppUserModelId -notmatch $browserPattern) { continue }
		try { $p = Await ($s.TryGetMediaPropertiesAsync()) $propsType } catch { continue }
		if ($null -eq $p -or [string]::IsNullOrEmpty($p.Title)) { continue }
		foreach ($t in (BrowserTitles)) {
			if ($t -like '*Spotify*' -or $t.StartsWith($p.Title + ' ' + $bullet + ' ')) { return $s }
		}
		if ($null -eq $fallback -and -not [string]::IsNullOrEmpty($p.AlbumTitle)) { $fallback = $s }
	}
	return $fallback
}

$lastKey = $null
$artPath = ''
$artIndex = 0
while ($true) {
	# Stop with the game, even if it crashed without closing us.
	if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { exit 0 }
	try {
		$session = $null
		$web = $false
		$sessions = @($manager.GetSessions())
		foreach ($s in $sessions) {
			if ($s.SourceAppUserModelId -like '*Spotify*') { $session = $s; break }
		}
		if ($null -eq $session -and $Only -eq 'spotify') {
			$session = FindSpotifyWeb $sessions
			$web = $null -ne $session
		}
		if ($null -eq $session -and $Only -ne 'spotify') { $session = $manager.GetCurrentSession() }

		if (Test-Path -LiteralPath $CmdFile) {
			$cmd = ([IO.File]::ReadAllText($CmdFile)).Trim()
			Remove-Item -LiteralPath $CmdFile -ErrorAction SilentlyContinue
			if ($null -ne $session) {
				switch ($cmd) {
					'toggle' { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
					'next' { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
					'previous' { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
				}
			}
		}

		if ($null -eq $session) {
			Say @{ status = 'none' }
		} else {
			$props = Await ($session.TryGetMediaPropertiesAsync()) $propsType
			$timeline = $session.GetTimelineProperties()
			$playback = $session.GetPlaybackInfo().PlaybackStatus
			$status = 'none'
			if ($playback -eq $playingStatus) { $status = 'playing' } elseif ($playback -eq $pausedStatus) { $status = 'paused' }
			$position = $timeline.Position.TotalMilliseconds
			# The position is only as fresh as the player's last update; move it on to now while playing.
			if ($status -eq 'playing' -and $timeline.LastUpdatedTime.Year -gt 2000) {
				$position += ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds
			}
			$key = "$($props.Title)|$($props.Artist)|$($props.AlbumTitle)"
			if ($key -ne $lastKey) {
				$lastKey = $key
				$artPath = ''
				if ($null -ne $props.Thumbnail) {
					try {
						$stream = Await ($props.Thumbnail.OpenReadAsync()) $streamType
						$net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
						$artIndex++
						$file = Join-Path $ArtDir ('art' + ($artIndex % 4) + '.img')
						$out = [IO.File]::Create($file)
						$net.CopyTo($out)
						$out.Close()
						$net.Dispose()
						$artPath = $file
					} catch {
						$artPath = ''
					}
				}
			}
			Say @{
				status = $status
				title = [string]$props.Title
				artist = [string]$props.Artist
				album = [string]$props.AlbumTitle
				position = [long]$position
				duration = [long]$timeline.EndTime.TotalMilliseconds
				art = $artPath
				player = $(if ($web) { 'Spotify web player (' + $session.SourceAppUserModelId + ')' } else { [string]$session.SourceAppUserModelId })
			}
		}
	} catch {
		Say @{ status = 'error'; message = "$_" }
		Start-Sleep -Milliseconds 1500
	}
	Start-Sleep -Milliseconds 400
}
