package appserver.job.impl;

import appserver.job.Tool;

public class Fibonacci implements Tool {
    
    @Override
    public Object go(Object parameters) {
        
        return fib(((Integer) parameters));
        
        
    }
    
    private static int fib(int n) {
        if (n == 1)
            return 0;
        if (n == 2)
            return 1;
 
        return fib(n - 1) + fib(n - 2);
    }
}