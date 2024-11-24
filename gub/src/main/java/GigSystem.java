import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.io.IOException;
import java.util.Properties;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.Vector;

public class GigSystem {

    public static void main(String[] args) {

        // You should only need to fetch the connection details once
        // You might need to change this to either getSocketConnection() or getPortConnection() - see below
        Connection conn = getSocketConnection();

        boolean repeatMenu = true;
        
        while(repeatMenu){
            System.out.println("_________________________");
            System.out.println("________GigSystem________");
            System.out.println("_________________________");

            
            System.out.println("q: Quit");

            String menuChoice = readEntry("Please choose an option: ");

            if(menuChoice.length() == 0){
                //Nothing was typed (user just pressed enter) so start the loop again
                continue;
            }
            char option = menuChoice.charAt(0);

            /**
             * If you are going to implement a menu, you must read input before you call the actual methods
             * Do not read input from any of the actual task methods
             */
            switch(option){
                case '1':
                    break;

                case '2':
                    break;
                case '3':
                    break;
                case '4':
                    break;
                case '5':
                    break;
                case '6':
                    break;
                case '7':
                    break;
                case '8':
                    break;
                case 'q':
                    repeatMenu = false;
                    break;
                default: 
                    System.out.println("Invalid option");
            }
        }
    }

    /*
     * You should not change the names, input parameters or return types of any of the predefined methods in GigSystem.java
     * You may add extra methods if you wish (and you may overload the existing methods - as long as the original version is implemented)
     */

    public static String[][] task1(Connection conn, int gigID){
        return null;
    }

    public static void task2(Connection conn, String venue, String gigTitle, LocalDateTime gigStart, int adultTicketPrice, ActPerformanceDetails[] actDetails){
        
    }

    public static void task3(Connection conn, int gigid, String name, String email, String ticketType){
        
    }

    public static String[][] task4(Connection conn, int gigID, String actName){
        return null;
    }

    public static String[][] task5(Connection conn){
        return null;
    }

    public static String[][] task6(Connection conn){
        return null;
    }

    public static String[][] task7(Connection conn){
        return null;
    }

    public static String[][] task8(Connection conn){
        return null;
    }

    /**
     * Prompts the user for input
     * @param prompt Prompt for user input
     * @return the text the user typed
     */

    private static String readEntry(String prompt) {
        
        try {
            StringBuffer buffer = new StringBuffer();
            System.out.print(prompt);
            System.out.flush();
            int c = System.in.read();
            while(c != '\n' && c != -1) {
                buffer.append((char)c);
                c = System.in.read();
            }
            return buffer.toString().trim();
        } catch (IOException e) {
            return "";
        }

    }
     
    /**
    * Gets the connection to the database using the Postgres driver, connecting via unix sockets
    * @return A JDBC Connection object
    */
    public static Connection getSocketConnection(){
        Properties props = new Properties();
        props.setProperty("socketFactory", "org.newsclub.net.unix.AFUNIXSocketFactory$FactoryArg");
        props.setProperty("socketFactoryArg",System.getenv("HOME") + "/cs258-postgres/postgres/tmp/.s.PGSQL.5432");
        Connection conn;
        try{
          conn = DriverManager.getConnection("jdbc:postgresql://localhost/cwk", props);
          return conn;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gets the connection to the database using the Postgres driver, connecting via TCP/IP port
     * @return A JDBC Connection object
     */
    public static Connection getPortConnection() {
        
        String user = "postgres";
        String passwrd = "password";
        Connection conn;

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException x) {
            System.out.println("Driver could not be loaded");
        }

        try {
            conn = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/cwk?user="+ user +"&password=" + passwrd);
            return conn;
        } catch(SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            System.out.println("Error retrieving connection");
            return null;
        }
    }

    public static String[][] convertResultToStrings(ResultSet rs){
        Vector<String[]> output = null;
        String[][] out = null;
        try {
            int columns = rs.getMetaData().getColumnCount();
            output = new Vector<String[]>();
            int rows = 0;
            while(rs.next()){
                String[] thisRow = new String[columns];
                for(int i = 0; i < columns; i++){
                    thisRow[i] = rs.getString(i+1);
                }
                output.add(thisRow);
                rows++;
            }
            // System.out.println(rows + " rows and " + columns + " columns");
            out = new String[rows][columns];
            for(int i = 0; i < rows; i++){
                out[i] = output.get(i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static void printTable(String[][] out){
        int numCols = out[0].length;
        int w = 20;
        int widths[] = new int[numCols];
        for(int i = 0; i < numCols; i++){
            widths[i] = w;
        }
        printTable(out,widths);
    }

    public static void printTable(String[][] out, int[] widths){
        for(int i = 0; i < out.length; i++){
            for(int j = 0; j < out[i].length; j++){
                System.out.format("%"+widths[j]+"s",out[i][j]);
                if(j < out[i].length - 1){
                    System.out.print(",");
                }
            }
            System.out.println();
        }
    }

}