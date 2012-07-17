package hre.config;

import static hre.System.*;

public class BooleanSetting {

  private boolean value;
  
  public BooleanSetting(boolean default_setting){
    value=default_setting;
  }
  
  private class EnableOption extends AbstractOption {
    public EnableOption(String help) {
      super(false,help);
    }
    public void pass(){
      value=true;
    }
  }
  private class DisableOption extends AbstractOption {
    public DisableOption(String help) {
      super(false,help);
    }
    public void pass(){
      value=false;
    }
  }
  private class AssignOption extends AbstractOption {
    public AssignOption(String help) {
      super(true,help);
    }
    public void pass(String value){
      if (value.equalsIgnoreCase("true")||value.equalsIgnoreCase("on")){
        set(true);
      } else if (value.equalsIgnoreCase("false")||value.equalsIgnoreCase("off")){
        set(false);
      }
      Fail("cannot parse %s",value);
    }
  }
  
  public Option getEnable(String help){
    return new EnableOption(help);
  }
  public Option getDisable(String help){
    return new DisableOption(help);
  }
  public Option getAssign(String help){
    return new AssignOption(help);
  }
  public boolean get(){
    return value;
  }
  
  public void set(boolean value){
    this.value=value;
  }
}
