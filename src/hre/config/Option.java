package hre.config;

public interface Option {

  public boolean needsArgument();

  public void pass();

  public void pass(String arg);

  public String getHelp();

}
