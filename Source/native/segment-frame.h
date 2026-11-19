#ifndef RAVEN_SEGMENT_FRAME_H
#define RAVEN_SEGMENT_FRAME_H
#include <stdint.h>
/* Physical TL, TR, BR, BL; left and right controllers have different orientation. */
static const int segment_groups[2][4]={{3,0,1,2},{0,1,2,3}};
static unsigned segment_hex(char c){return c<='9'?(unsigned)(c-'0'):(unsigned)((c|32)-'a'+10);}
static void segment_frame(const char *packed,int side,uint8_t pwm[12],uint8_t controls[12]){
 for(int pos=0;pos<4;pos++){
  int group=segment_groups[side][pos],lit=0;
  for(int c=0;c<3;c++){int at=side*24+pos*6+c*2;pwm[group*3+c]=(uint8_t)(segment_hex(packed[at])*16+segment_hex(packed[at+1]));lit|=pwm[group*3+c];}
  /* Zero PWM is also black/zero brightness. Disable all three driver channels. */
  for(int c=0;c<3;c++)controls[group*3+c]=lit?1:0;
 }
}
#endif
