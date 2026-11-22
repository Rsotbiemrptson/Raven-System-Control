#define _GNU_SOURCE
#include <sys/inotify.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <poll.h>
#include <signal.h>
#include <time.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#ifndef ZONE_HOST_TEST
#include <sys/system_properties.h>
#endif

/* Firmware 400 repaints on the LEFT ring/side settings. Observe completion of
 * those writes before the independent frame, instead of sleeping one second.
 * Missing/coalesced events keep the previous full settling timeout. */
static const char *keys[4]={"left_joystick_light_enabled","right_joystick_light_enabled","left_handle_light_enabled","right_handle_light_enabled"};
static char paths[4][512]; /* enable L, brightness L, enable R, brightness R */
static long millis(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec*1000+t.tv_nsec/1000000;}
static int number(const char *s,int max){char *e;long n=strtol(s,&e,10);return *s&&!*e&&n>=0&&n<=max?(int)n:-1;}
static int gate(void){
#ifdef ZONE_HOST_TEST
 const char *root=getenv("RAVEN_ZONE_TEST_ROOT");if(!root)return -1;
 for(int i=0;i<4;i++)if(snprintf(paths[i],sizeof(paths[i]),"%s/%s",root,(const char*[]){"left-enable","left-brightness","right-enable","right-brightness"}[i])>=(int)sizeof(paths[i]))return -1;
#else
 char p[PROP_VALUE_MAX];if(getuid()!=0||__system_property_get("ro.product.model",p)<=0||strcmp(p,"Odin2"))return -1;
 if(__system_property_get("ro.build.version.sdk",p)<=0||strcmp(p,"33"))return -1;
 if(__system_property_get("ro.build.display.id",p)<=0||strcmp(p,"Odin2_Custom_V1.0.0.400"))return -1;
 for(int i=0;i<4;i++)snprintf(paths[i],sizeof(paths[i]),"/sys/class/sn3112%c/led/%s",i<2?'l':'r',i%2?"brightness":"enable");
#endif
 for(int i=0;i<4;i++){int f=open(paths[i],O_WRONLY|O_CLOEXEC|O_NOFOLLOW);if(f<0)return -1;struct stat st;int ok=fstat(f,&st)==0&&S_ISREG(st.st_mode);close(f);if(!ok)return -1;}return 0;
}
static int setting(const char *op,const char *key,const char *value,char out[64]){
 int pipefd[2];if(pipe2(pipefd,O_CLOEXEC|O_NONBLOCK))return -1;pid_t pid=fork();if(pid<0){close(pipefd[0]);close(pipefd[1]);return -1;}
 if(pid==0){close(pipefd[0]);int null=open("/dev/null",O_RDWR);dup2(pipefd[1],STDOUT_FILENO);dup2(null,STDERR_FILENO);close(pipefd[1]);close(null);
#ifdef ZONE_HOST_TEST
 const char *program=getenv("RAVEN_ZONE_TEST_SETTINGS");if(!program)_exit(127);
#else
 const char *program="/system/bin/settings";
#endif
 execl(program,"settings","--user","0",op,"system",key,value,(char*)NULL);_exit(127);}
 close(pipefd[1]);int status=0,done=0;size_t used=0;long deadline=millis()+2000;out[0]=0;
 while(millis()<deadline){char b[128];ssize_t n;while((n=read(pipefd[0],b,sizeof(b)))>0){size_t copy=(size_t)n;if(copy>63-used)copy=63-used;memcpy(out+used,b,copy);used+=copy;out[used]=0;}if(waitpid(pid,&status,WNOHANG)==pid){done=1;break;}struct pollfd p={.fd=pipefd[0],.events=POLLIN};poll(&p,1,5);}
 if(!done){kill(pid,SIGKILL);while(waitpid(pid,&status,0)<0&&errno==EINTR){}}
 char tail[128];ssize_t n;while((n=read(pipefd[0],tail,sizeof(tail)))>0){size_t copy=(size_t)n;if(copy>63-used)copy=63-used;memcpy(out+used,tail,copy);used+=copy;out[used]=0;}
 close(pipefd[0]);while(used&&(out[used-1]=='\n'||out[used-1]=='\r'))out[--used]=0;return done&&WIFEXITED(status)&&WEXITSTATUS(status)==0?0:-1;
}
static int write_node(int i,const char *s){int f=open(paths[i],O_WRONLY|O_CLOEXEC|O_NOFOLLOW);if(f<0)return -1;
#ifdef ZONE_HOST_TEST
 if(ftruncate(f,0)){close(f);return -1;}
#endif
 size_t n=strlen(s);ssize_t result=write(f,s,n);close(f);return result==(ssize_t)n?0:-1;}
static void blank(void){write_node(0,"0\n");write_node(2,"0\n");}
int main(int argc,char **argv){
 if(argc!=11){puts("ERROR Invalid Odin2 frame");return 1;}int rgb[4][3],level[4];const char *mask=argv[9],*brightness=argv[10];
 if(strlen(mask)!=4||strspn(mask,"01")!=4){puts("ERROR Invalid mask");return 1;}
 if(strcmp(brightness,"null")){char *e;double v=strtod(brightness,&e);if(!*brightness||*e||strspn(brightness,"0123456789.")!=strlen(brightness)||!(v>=0&&v<=1)){puts("ERROR Invalid stock brightness");return 1;}}
 for(int i=0;i<4;i++){if(strlen(argv[i+1])!=6||strspn(argv[i+1],"0123456789abcdefABCDEF")!=6||(level[i]=number(argv[i+5],100))<0){puts("ERROR Invalid RGB or level");return 1;}unsigned color=(unsigned)strtoul(argv[i+1],NULL,16);for(int c=0;c<3;c++)rgb[i][c]=(int)((color>>(16-8*c))&255)*level[i]/100;}
 if(gate()){puts("ERROR Compatible Odin2 interfaces unavailable");return 1;}
 int watch=inotify_init1(IN_NONBLOCK|IN_CLOEXEC),wd[4],observed=watch>=0;for(int i=0;i<4;i++){wd[i]=watch>=0?inotify_add_watch(watch,paths[i],IN_CLOSE_WRITE):-1;if(wd[i]<0)observed=0;}
 char old[4],out[64];for(int i=0;i<4;i++){if(setting("get",keys[i],NULL,out))goto fail;old[i]=!strcmp(out,"1")?'1':'0';}
 int expected=(old[0]!=mask[0])+(old[2]!=mask[2]);
 /* Right flags are durable state, but do not trigger the verified OEM repaint.
  * Enable left groups before disabling others so nonempty frames stay nonempty. */
 int order[4]={1,3,0,2};for(int k=0;k<2;k++){int i=order[k];if(old[i]!=mask[i]&&setting("put",keys[i],mask[i]=='1'?"1":"0",out))goto fail;}
 for(int on=1;on>=0;on--)for(int k=2;k<4;k++){int i=order[k];if(mask[i]-'0'==on&&old[i]!=mask[i]&&setting("put",keys[i],on?"1":"0",out))goto fail;}
 long start=millis(),last=start;int count[4]={0},ack=expected==0;
 while(expected&&millis()-start<1000){if(observed){struct pollfd p={.fd=watch,.events=POLLIN};if(poll(&p,1,5)>0){char b[4096];ssize_t n=read(watch,b,sizeof(b));for(ssize_t at=0;at<n;){struct inotify_event *e=(void*)(b+at);if(e->mask&IN_Q_OVERFLOW)observed=0;for(int i=0;i<4;i++)if(e->wd==wd[i]&&(e->mask&IN_CLOSE_WRITE))count[i]++;at+=sizeof(*e)+e->len;}last=millis();}}
  else {struct timespec delay={.tv_nsec=5000000};nanosleep(&delay,NULL);}
  int full=count[0]>=expected&&count[2]>=expected;
  if(mask[0]=='1'||mask[2]=='1')full=full&&count[1]>=expected*2&&count[3]>=expected*2;
  if(observed&&full&&millis()-last>=20){ack=1;break;}}
 long waited=millis()-start;if(watch>=0){close(watch);watch=-1;}
 /* Never repaint over a newer OEM color, brightness or power request. */
 if(setting("get","led_light_picker_color",NULL,out)||strcmp(out,"-16777216"))goto changed;
 if(setting("get","led_light_brightness_percent",NULL,out)||strcmp(out,brightness))goto changed;
 for(int i=0;i<4;i++){if(setting("get",keys[i],NULL,out)||(mask[i]=='1'?strcmp(out,"1"):(strcmp(out,"0")&&strcmp(out,"null"))))goto changed;}
 int active=strcmp(mask,"0000")!=0;if(active&&(write_node(0,"1\n")||write_node(2,"1\n")))goto fail;
 for(int i=0;i<4;i++){char value[64];int on=mask[i]=='1';snprintf(value,sizeof(value),"%d-%d:%d:%d:255\n",i<2?2:1,on?rgb[i][0]:0,on?rgb[i][1]:0,on?rgb[i][2]:0);if(write_node(i%2?3:1,value))goto fail;}
 if(!active){blank();}printf("OK Direct lighting mask=%s settle=%s wait=%ldms\n",mask,expected?(ack?"event":"fallback"):"unchanged",waited);return 0;
changed: if(watch>=0)close(watch);puts("ERROR Stock lighting changed; no custom frame written");return 1;
fail: if(watch>=0)close(watch);blank();puts("ERROR Odin2 frame failed; output disabled and recovery retained");return 1;
}
