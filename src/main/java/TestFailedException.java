class TestFailedException extends Exception{
    public TestFailedException(String provided, String expected){
        super("Found: " + provided + ", Expected: " + expected);
    }
}