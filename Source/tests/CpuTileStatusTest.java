package dev.odin.controls;
public final class CpuTileStatusTest {
 static void same(String want,String got){if(!want.equals(got))throw new AssertionError(want+" != "+got);}
 public static void main(String[] args){
  for(int mode=0;mode<3;mode++){
   String name=PerformanceProfile.STOCK_NAMES[mode];
   same(name,CpuTileStatus.label(mode,false,-1,null));
   same(name,CpuTileStatus.label(mode,true,mode,name));
   same("Saved underclock",CpuTileStatus.label(mode,true,mode,"Saved underclock"));
   same(name,CpuTileStatus.label(mode,true,(mode+1)%3,"Old profile"));
   if(CpuTileStatus.mode(""+mode)!=mode)throw new AssertionError("Vendor mode parse");
  }
  same("Performance",CpuTileStatus.label(1,true,1,"Performance"));
  same("High Performance",CpuTileStatus.label(2,true,2,"High Performance"));
  same("Saved underclock",CpuTileStatus.label(-1,true,1,"Saved underclock"));
  same("CPU profiles",CpuTileStatus.label(-1,false,-1,null));
  same("CPU profiles",CpuTileStatus.label(-1,true,-1,"Stale name"));
  same("Normal",CpuTileStatus.label(0,true,0,""));
  for(String invalid:new String[]{"",null,"null","3","-1","Performance","1\n"})if(CpuTileStatus.mode(invalid)!=-1)throw new AssertionError("Invalid vendor mode accepted");
  System.out.println("PASS actual stock modes, saved profiles in every base mode, OEM handoff and unknown-mode labels");
 }
}
