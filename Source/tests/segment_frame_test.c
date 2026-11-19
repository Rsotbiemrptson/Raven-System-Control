#include <assert.h>
#include <string.h>
#include <stdio.h>
#include "../native/segment-frame.h"
int main(void){
 const char *initial="7F0000007F0000007F7F40007F0000007F0000007F7F4000";
 for(int side=0;side<2;side++)for(int position=0;position<4;position++){
  uint8_t before[12],before_control[12],after[12],control[12];char changed[49];
  segment_frame(initial,side,before,before_control);strcpy(changed,initial);memset(changed+side*24+position*6,'0',6);segment_frame(changed,side,after,control);
  int disabled_group=side==0?(position+3)%4:position;
  for(int c=0;c<12;c++){if(c/3==disabled_group){assert(after[c]==0);assert(control[c]==0);}else{assert(after[c]==before[c]);assert(control[c]==before_control[c]);}}
 }
 puts("PASS both ring maps: off sections have zero PWM and disabled RGB channels; all peer channels retain their output");return 0;
}
