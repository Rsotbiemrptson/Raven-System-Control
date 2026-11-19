#define _GNU_SOURCE
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <linux/i2c.h>
#include <linux/i2c-dev.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include "segment-frame.h"
static int fd[2]={-1,-1};
static int syswrite(int side,const char *name,const char *value){char p[128];snprintf(p,sizeof(p),"/sys/class/sn3112%c/led/%s",side?'r':'l',name);int f=open(p,O_WRONLY|O_CLOEXEC|O_NOFOLLOW);if(f<0)return -1;size_t n=strlen(value);ssize_t w=write(f,value,n);close(f);return w==(ssize_t)n?0:-1;}
static int reg(int side,uint8_t address,uint8_t value){uint8_t data[2]={address,value};struct i2c_msg m={.addr=0x3c,.len=2,.buf=data};struct i2c_rdwr_ioctl_data x={.msgs=&m,.nmsgs=1};return ioctl(fd[side],I2C_RDWR,&x)==1?0:-1;}
static int discover(void){char model[PROP_VALUE_MAX];if(getuid()!=0||__system_property_get("ro.product.model",model)<=0||strcmp(model,"Odin3"))return -1;
 int buses[2]={-1,-1};for(int b=0;b<256;b++){char p[128],name[40];snprintf(p,sizeof(p),"/sys/bus/i2c/devices/%d-003c/name",b);int f=open(p,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);if(f<0)continue;ssize_t n=read(f,name,sizeof(name)-1);close(f);if(n<=0)continue;name[n]=0;name[strcspn(name,"\r\n")]=0;for(int s=0;s<2;s++)if(!strcmp(name,s?"htr3212r":"htr3212l")){if(buses[s]>=0)return -1;buses[s]=b;}}
 for(int s=0;s<2;s++){if(buses[s]<0)return -1;char p[128];snprintf(p,sizeof(p),"/dev/i2c-%d",buses[s]);fd[s]=open(p,O_RDWR|O_CLOEXEC|O_NOFOLLOW);if(fd[s]<0)return -1;struct stat st;unsigned long funcs=0;if(fstat(fd[s],&st)||!S_ISCHR(st.st_mode)||ioctl(fd[s],I2C_FUNCS,&funcs)||!(funcs&I2C_FUNC_I2C))return -1;snprintf(p,sizeof(p),"/sys/class/sn3112%c/led/enable",s?'r':'l');if(access(p,W_OK))return -1;}return 0;
}
int main(int argc,char **argv){
 if(discover()){puts("ERROR Compatible HTR3212 ring interfaces unavailable");return 1;}
 if(argc==2&&!strcmp(argv[1],"probe")){puts("OK HTR3212 physical segment mapping v1");return 0;}
 if(argc!=3||strlen(argv[1])!=48||strlen(argv[2])!=3||argv[2][1]!=','||!strchr("01",argv[2][0])||!strchr("01",argv[2][2])||strspn(argv[1],"0123456789abcdefABCDEF")!=48){puts("ERROR Invalid segment request");return 1;}
 int failed=0;for(int s=0;s<2&&!failed;s++){
  if(argv[2][s*2]=='0'){failed=syswrite(s,"enable","0\n");continue;}
  if(syswrite(s,"enable","1\n")||reg(s,0,0)){failed=1;break;}
  uint8_t pwm[12],controls[12];segment_frame(argv[1],s,pwm,controls);
  for(int c=0;c<12&&!failed;c++)if(reg(s,0x0d+c,pwm[c]))failed=1;
  for(int c=0;c<12&&!failed;c++)if(reg(s,0x32+c,controls[c]))failed=1;
  if(!failed&&(reg(s,0x25,0)||reg(s,0,1)))failed=1;
 }
 if(failed){syswrite(0,"enable","0\n");syswrite(1,"enable","0\n");puts("ERROR Segment write failed; both rings disabled");return 1;}
 puts("OK Ring segments applied");return 0;
}
