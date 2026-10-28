package dev.odin.controls;
public final class AppearanceColorsTest {
 public static void main(String[] args){int count=0;for(int r=0;r<=255;r+=17)for(int g=0;g<=255;g+=17)for(int b=0;b<=255;b+=17){int color=0xff000000|(r<<16)|(g<<8)|b;if(AppearanceColors.contrast(AppearanceColors.ink(color),color)<4.5)throw new AssertionError("Button contrast");for(int surface:new int[]{0xffFFFFFF,0xff272B32})if(AppearanceColors.contrast(AppearanceColors.readable(color,surface),surface)<4.5)throw new AssertionError("Text contrast");count++;}System.out.println("PASS "+count+" accent colors: button labels and light/dark surface text meet 4.5:1 contrast");}
}
