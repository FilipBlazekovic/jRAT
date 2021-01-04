package jRAT.victim;

public class BackgroundExecutor extends Thread {

    private Class moduleClass;
    private String methodName;
    private Class[] methodArgTypes;
    private Object[] methodArgs;

    public BackgroundExecutor(Class moduleClass, String methodName, Class[] methodArgTypes, Object[] methodArgs)
    {
        this.moduleClass    = moduleClass;
        this.methodName     = methodName;
        this.methodArgTypes = methodArgTypes;
        this.methodArgs     = methodArgs;
    }

    @Override
    public void run()
    {
        try { moduleClass.getMethod(methodName, methodArgTypes).invoke(null, methodArgs); }
        catch (Exception ex) {}
    }
}
