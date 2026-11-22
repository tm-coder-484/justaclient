package dev.justaclient.render;
import dev.justaclient.feature.impl.FullbrightFeature;
import dev.justaclient.feature.impl.LightColorFeature;
public final class LightTint {
    public static int tint(int block,int sky,int abgr) {
        var feature=LightColorFeature.get();
        if(feature==null) return abgr;
        float[] b=feature.blockTint(),s=feature.skyTint();float full=FullbrightFeature.amount();
        float bw=b[3]*block/15f,sw=s[3]*sky/15f;
        float[] rgb={(abgr&255)/255f,(abgr>>8&255)/255f,(abgr>>16&255)/255f};
        for(int i=0;i<3;i++) {
            rgb[i]*=(1-bw)+b[i]*bw;
            rgb[i]*=(1-sw)+s[i]*sw;
            rgb[i]+= (1-rgb[i])*full;
        }
        return abgr&0xff000000 | Math.round(rgb[2]*255)<<16 | Math.round(rgb[1]*255)<<8 | Math.round(rgb[0]*255);
    }
}
