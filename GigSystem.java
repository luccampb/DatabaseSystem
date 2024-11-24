import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.io.IOException;
import java.util.Properties;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.sql.Timestamp;
import java.sql.Types;
import java.sql.Time;
import java.util.Vector;

import javax.print.DocFlavor.STRING;

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

            System.out.println("1: Return line-up for a given gigID");
            System.out.println("2: Task 2");
            System.out.println("3: Book a ticket");
            System.out.println("4: Cancel an act");
            System.out.println("5: Find tickets needed to sell");
            System.out.println("6: Find how many tickets sold");
            System.out.println("7: Regular customers");
            System.out.println("8: Economically feasible gigs");
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
                    int id1;
                    try {
                        id1 = Integer.parseInt(readEntry("Please enter a gig ID: "));;
                    }
                    catch (NumberFormatException e) {
                        System.out.println("Not valid gigID");
                        break;
                    }
                    task1(conn, id1);
                    break;
                case '2':
                    break;
                case '3':
                    int id;
                    try {
                        id = Integer.parseInt(readEntry("Please enter a gig ID: "));;
                    }
                    catch (NumberFormatException e) {
                        System.out.println("Not valid gigID");
                        break;
                    }
                    String name = readEntry("Please enter a customer name: ");
                    String email = readEntry("Please enter a customer email: ");
                    String ticket = readEntry("Please enter a ticket type: ");
                    task3(conn, id, name, email, ticket);
                    break;
                case '4':
                    int id2;
                    try {
                        id2 = Integer.parseInt(readEntry("Please enter a gig ID: "));;
                    }
                    catch (NumberFormatException e) {
                        System.out.println("Not valid gigID");
                        break;
                    }
                    String actname = readEntry("Please enter an act name: ");
                    task4(conn, id2, actname);
                    break;
                case '5':
                    task5(conn);
                    break;
                case '6':
                    task6(conn);
                    break;
                case '7':
                    task7(conn);
                    break;
                case '8':
                    task8(conn);
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
        //Query projects the necessary information from act joined with act_gig on actID. Orders by ontime so that the result will begin with the first act in an event
        //Time + make_interval(mins => duration) adds the duration time to the ontime to get the finish time
        String selectQuery = "SELECT actname, ontime::TIME, ontime::TIME + make_interval(mins => duration) FROM act NATURAL JOIN act_gig WHERE act_gig.gigid = ? ORDER BY ontime ASC";
        try{
            //Creates the prepared statement and makes it scrollable so that we can access the final item in the result set
            PreparedStatement preparedStatement = conn.prepareStatement(selectQuery, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            preparedStatement.setInt(1, gigID);
            ResultSet gig = preparedStatement.executeQuery();
            String[][] lineup = null;
            //Check if the query returned something
            if (gig.next()) 
            {
                //sets result set pointer to the last row to get the length of the result set
                gig.last();
                //get row gets the number of the current row. Since the pointer is at the final row
                //it gets the length of the result set
                lineup = new String[gig.getRow()][3];
                //pointer used in iteration
                int pointer=0;
                //resets result set pointer to the first row
                gig.first();
                //do while instead of while since gig.first() puts the pointer at the start of the result set
                do {
                    lineup[pointer][0] = gig.getString(1);
                    lineup[pointer][1] = gig.getTime(2).toString();
                    lineup[pointer][2] = gig.getTime(3).toString();
                    //increment counter
                    pointer++;
                } while (gig.next());
            }
            //Closes the statement and result set then returns the desired output     
            preparedStatement.close();
            gig.close();
            return lineup;
        }catch(SQLException e){
            //Outputs the SQL error if there is one
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static void task2(Connection conn, String venue, String gigTitle, LocalDateTime gigStart, int adultTicketPrice, ActPerformanceDetails[] actDetails){
        //Gets the venue id which is required for inserting a gig
        String selectVenue = "SELECT venueID FROM venue WHERE venuename = ?";
        //Gets the highest gigid in gig so we can insert a new gig
        //Could use DEFAULT inserting into gig, but then it becomes more difficult
        //to add an act to act_gig
        String selectGig = "SELECT max(gigID) FROM gig";
        try{
            //Does not automatically commit changes until turned back on so we can rollback if there is an issue
            conn.setAutoCommit(false);
            PreparedStatement preparedVenue = conn.prepareStatement(selectVenue);
            preparedVenue.setString(1, venue);
            ResultSet ven = preparedVenue.executeQuery();
            //Not using prepared statement as it is only executed once with no user input
            Statement getMaxGigId = conn.createStatement();
            ResultSet gigid = getMaxGigId.executeQuery(selectGig);
            gigid.next();
            int newid = gigid.getInt(1) + 1;
            //Checks if there is a venue matching the one given
            if (ven.next()) {
                //Insert statement to create the gig
                String insertGig = "INSERT INTO gig VALUES (?,?,?,?,'GoingAhead')";
                PreparedStatement preparedGig = conn.prepareStatement(insertGig);
                preparedGig.setInt(1, newid);
                //Gets venue id
                preparedGig.setInt(2, ven.getInt(1));
                preparedGig.setString(3, gigTitle); 
                preparedGig.setTimestamp(4, Timestamp.valueOf(gigStart));
                preparedGig.executeUpdate();
                preparedGig.close();
                //Inserts new data into act_gig
                String insertActgig = "INSERT INTO act_gig VALUES (?,?,?,?,?)";
                PreparedStatement preparedActgig = conn.prepareStatement(insertActgig);
                //Iterates through actDetails and adds each act into act_gig
                for(int i = 0; i < actDetails.length ; i++) {
                    preparedActgig.setInt(1, actDetails[i].getActID());
                    preparedActgig.setInt(2, newid);
                    preparedActgig.setInt(3, actDetails[i].getFee()); 
                    //Timestamp.valueof() converts a LocalDateTime object into a TIMESTAMP
                    preparedActgig.setTimestamp(4, Timestamp.valueOf(actDetails[i].getOnTime()));
                    preparedActgig.setInt(5, actDetails[i].getDuration());
                    preparedActgig.executeUpdate();
                }
                preparedActgig.close();
                //Inserts the adult ticket for the new gig into gig_ticket
                String insertTicket = "INSERT INTO gig_ticket VALUES (?,?,?)";
                PreparedStatement preparedGigticket = conn.prepareStatement(insertTicket);
                preparedGigticket.setInt(1, newid);
                preparedGigticket.setString(2, "A");
                preparedGigticket.setInt(3, adultTicketPrice);
                preparedGigticket.executeUpdate();
                preparedGigticket.close();
                //If the method has got to this point, there have been no errors with the new data inserted, so we are safe to commit the changes
                conn.setAutoCommit(true);
            }
            //Close any open objects
            gigid.close();
            preparedVenue.close();
            ven.close();
        }catch(SQLException e){
            //If an error has occurred, rollback the database to before the method was called
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            try {
                //conn.rollback() needs to be in a try catch to work
                conn.rollback();
            }catch(SQLException ex) {
                System.err.format("SQL State: %s\n%s\n", ex.getSQLState(), ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public static void task3(Connection conn, int gigid, String name, String email, String ticketType){
        try{
            //Turn of automatic commits
            conn.setAutoCommit(false);
            //Can just use value 0 for the ticket price since the CheckCostOfTicket Trigger will set it to the value matching gig_ticket
            //If the gig or ticket type doesn't exist then a trigger will fire and cancel the insert
            String insertTicket = "INSERT INTO ticket VALUES (DEFAULT,?,?,0,?,?)";
            PreparedStatement preparedInsert = conn.prepareStatement(insertTicket);
            preparedInsert.setInt(1, gigid);
            preparedInsert.setString(2, ticketType);
            preparedInsert.setString(3, name);
            preparedInsert.setString(4, email);
            preparedInsert.executeUpdate();
            //Can turn on autocommit at this point since an error would put the program into the catch statement
            conn.setAutoCommit(true);
            preparedInsert.close();
        }catch(SQLException e){
            //Rolls back the database to before the method was called if there was an issue with insertion
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            try {
                conn.rollback();
            }catch(SQLException ex) {
                System.err.format("SQL State: %s\n%s\n", ex.getSQLState(), ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public static String[][] task4(Connection conn, int gigID, String actName){
        try {
            //Sets the default output to null
            String[][] resultString = null;
            //Turns off autocommitting
            conn.setAutoCommit(false);
            //Used to determine if a gig should be cancelled
            boolean cancel = false;
            //Checks if the act actually exists
            String actQuery = "SELECT actID FROM act WHERE actname = ?";
            PreparedStatement preparedAct = conn.prepareStatement(actQuery);
            preparedAct.setString(1, actName);
            ResultSet resultAct = preparedAct.executeQuery();
            if(resultAct.next()) {
                //Check act is in the gig given
                int actID = resultAct.getInt(1);
                String findAct = "SELECT * FROM act_gig WHERE gigid = ? AND actid = ?";
                PreparedStatement preparedFindAct = conn.prepareStatement(findAct);
                preparedFindAct.setInt(1, gigID);
                preparedFindAct.setInt(2, actID);
                ResultSet rs = preparedFindAct.executeQuery();
                if(rs.next()) {
                    //Determine if the act is the headliner using the plpgsql function DetermineHeadliner()
                    String headline = "{? = call DetermineHeadliner(?, ?)}";
                    CallableStatement func = conn.prepareCall(headline);
                    func.registerOutParameter(1, Types.BOOLEAN);
                    func.setInt(2, gigID);
                    func.setInt(3, actID);
                    func.execute();
                    //Gig should be cancelled if the act being removed is the headliner, so set cancel to true
                    if(func.getBoolean(1)) {
                        cancel = true;
                    } else {
                        //Create a savepoint, so if the delete violates some constraints, we don't delete it and instead cancel the whole gig
                        Savepoint beforeDelete = conn.setSavepoint();
                        try {          
                            //Deletes the act from the gig                  
                            String deleteQuery = "DELETE FROM act_gig WHERE actid = ? AND gigid = ?";
                            PreparedStatement preparedDelete = conn.prepareStatement(deleteQuery);
                            preparedDelete.setInt(1, actID);
                            preparedDelete.setInt(2, gigID);
                            preparedDelete.executeUpdate();                            
                            resultString = task1(conn, gigID);
                            preparedDelete.close();
                        }catch (SQLException exc) {
                            //If there was constraint violation, restore back to before the deletion and cancel the gig
                            conn.rollback(beforeDelete);
                            cancel = true;
                        }
                    }                    
                    if(cancel) {
                        //Cancel the gig
                        //Set all ticket costs for the gig to 0
                        String ticketUpdate = "UPDATE ticket SET cost = 0 WHERE gigid = ?";
                        PreparedStatement preparedTicket = conn.prepareStatement(ticketUpdate);
                        preparedTicket.setInt(1, gigID);
                        int numUpdates = preparedTicket.executeUpdate();
                        resultString = new String[1][numUpdates];
                        //Get all the customer emails for customers with affected tickets
                        //A customer can have multiple tickets for the same gig, so use DISTINCT to remove duplicates
                        String ticketQuery = "SELECT DISTINCT customeremail FROM ticket WHERE gigid = ? ORDER BY customeremail ASC";
                        PreparedStatement preparedTicketQuery = conn.prepareStatement(ticketQuery);
                        preparedTicketQuery.setInt(1, gigID);
                        ResultSet resultTicket = preparedTicketQuery.executeQuery();
                        int count = 0;
                        //Add all the affected customer's emails to the result string
                        while (resultTicket.next()) {
                            resultString[0][count] = resultTicket.getString(1);
                            count++;
                        }
                        preparedTicket.close();
                        preparedTicketQuery.close();
                        resultTicket.close();
                        //Finally, set the gigstatus to cancelled
                        String cancelGig = "UPDATE gig SET gigstatus = 'Cancelled' WHERE gigid = ?";
                        PreparedStatement preparedCancel = conn.prepareStatement(cancelGig);
                        preparedCancel.setInt(1, gigID);
                        preparedCancel.executeUpdate();
                        preparedCancel.close();
                    }
                    func.close();
                    //At this point it is ok to commit the changes
                    conn.setAutoCommit(true);
                } else {
                    rs.close();
                    preparedFindAct.close();
                    throw new SQLException();
                }
                rs.close();
                preparedFindAct.close();
            } else {
                preparedAct.close();
                resultAct.close();
                throw new SQLException();
            }
            preparedAct.close();
            resultAct.close();
            return resultString;
        }catch(SQLException e){
            //If there was an issue with any of this (outside of the delete with the savepoint), rollback the database to before the method was called
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            try {
                conn.rollback();
            }catch(SQLException ex) {
                System.err.format("SQL State: %s\n%s\n", ex.getSQLState(), ex.getMessage());
                ex.printStackTrace();
            }
            return null;
        }
    }

    public static String[][] task5(Connection conn){
        try {
            //Gets the gigids in ascending order
            String selectQuery = "SELECT gigid FROM gig ORDER BY gigid ASC";
            Statement selectStatement = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet rsSelect = selectStatement.executeQuery(selectQuery);
            //Sets output to null so we can just output this variable instead of doing if statements
            String[][] output = null;
            if(rsSelect.last()) {
                int numGigs = rsSelect.getRow();
                rsSelect.first();
                output = new String[numGigs][2];
                //Calculates remaining number of tickets to sell through the plpgsql CalculateRemainingCostOfGig function in schema.sql
                String calculateDifference = "{? = call CalculateRemainingCostOfGig(?)}";
                CallableStatement func = conn.prepareCall(calculateDifference);
                func.registerOutParameter(1, Types.INTEGER);
                int count = 0;
                //Iterates through each gig and adds their id and number of tickets to sell to the array
                do {
                    output[count][0] = Integer.toString(rsSelect.getInt(1));
                    func.setInt(2, count+1);
                    func.execute();
                    output[count][1] = Integer.toString(func.getInt(1));
                    count++;
                }while(rsSelect.next());
                func.close();
            }    
            rsSelect.close();
            return output;
        }catch (SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static String[][] task6(Connection conn){
        //Whole query written out so it is easier to understand
        /*WITH v AS (
            WITH w AS (
                SELECT DISTINCT act.actname, act.actid, to_char(act_gig.ontime::DATE, 'yyyy'), count(DISTINCT ticket.ticketid) 
                FROM act_gig NATURAL JOIN ticket NATURAL JOIN act 
                WHERE DetermineHeadliner(act_gig.gigid, act_gig.actid) AND IsGigGoingAhead(act_gig.gigid)
                GROUP BY act.actid, to_char(act_gig.ontime::DATE, 'yyyy'), act.actname 
                ORDER BY act.actid ASC
            ) 
            SELECT DISTINCT w.actname, w.to_char, sum(count) 
            FROM w 
            GROUP BY ROLLUP(w.actname, to_char) 
            ORDER BY actname, sum ASC
        )
        SELECT *, sum(CASE WHEN v.to_char IS NULL THEN v.sum END) over(PARTITION BY v.actname) total 
        FROM v         
        ORDER BY total, to_char;
        */
        String query = "WITH v AS (WITH w AS (SELECT DISTINCT act.actname, act.actid, to_char(act_gig.ontime::DATE, 'yyyy'), count(DISTINCT ticket.ticketid) FROM act_gig NATURAL JOIN ticket NATURAL JOIN act WHERE DetermineHeadliner(act_gig.gigid, act_gig.actid) AND IsGigGoingAhead(act_gig.gigid) GROUP BY act.actid, to_char(act_gig.ontime::DATE, 'yyyy'), act.actname ORDER BY act.actid ASC) SELECT DISTINCT w.actname, w.to_char, sum(count) FROM w GROUP BY ROLLUP(w.actname, to_char) ORDER BY actname, sum ASC) SELECT *, sum(CASE WHEN v.to_char IS NULL THEN v.sum END) over(PARTITION BY v.actname) total FROM v ORDER BY total, to_char";
        try {
            Statement task6Query = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet rs = task6Query.executeQuery(query);
            //Check the query resulted in something
            if(!rs.last()) {
                rs.close();
                throw new SQLException();
            }
            //The query has an extra column "total" which is used to order the result correctly
            //it isn't needed in the output so we just ignore it
            //Sets the pointer of the result set to the last item to get the length of the result set
            //The above query always has an extra row at the end which is not important for the output, hence the -1
            String[][] returnString = new String[rs.getRow()-1][3];
            //Resets the pointer of the result set to the first item in it
            rs.first();
            int count = 0;
            String index2insert;
            do {
                returnString[count][0] = rs.getString(1);
                index2insert = rs.getString(2);
                //In the select query, the rows which say "Total" in the sample output are just null, so update the label
                if(index2insert == null) {
                    index2insert = "Total";
                }
                returnString[count][1] = index2insert;
                returnString[count][2] = rs.getString(3);
                count++;
            } while (rs.next() && rs.getString(1) != null);
            rs.close();
            //If rs.getString(1) == null then we are at the aforementioned unimportant row at the end of the results
            return returnString;

        }catch(SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static String[][] task7(Connection conn){
        //Whole query written out so it is easier to understand
        /*
        WITH u AS (
            WITH w AS (
                WITH v AS (
                    SELECT act.actname, act.actid, act_gig.gigid, to_char(act_gig.ontime::DATE, 'yyyy') AS year, count(actid) OVER (PARTITION BY actname) 
                    FROM act_gig NATURAL JOIN act 
                    WHERE DetermineHeadliner(act_gig.gigid, act_gig.actid) 
                    ORDER BY act.actid ASC, year DESC
                    ) 
                SELECT v.actname, v.actid, v.gigid, v.year, v.count, ticket.customername, count(ticket.customername) OVER (PARTITION BY ticket.customername, v.actid) AS people 
                FROM v LEFT OUTER JOIN ticket ON v.gigid = ticket.gigid
                ) 
            SELECT w.actname, w.actid, w.gigid, w.year, w.count, w.customername, w.people 
            FROM w WHERE w.people >= w.count OR w.customername IS NULL 
            ORDER BY people DESC
            ) 
        SELECT DISTINCT u.actname, u.customername 
        FROM u 
        ORDER BY u.actname ASC;
        */
        String q = "WITH u AS (WITH w AS (WITH v AS (SELECT act.actname, act.actid, act_gig.gigid, to_char(act_gig.ontime::DATE, 'yyyy') AS year, count(actid) OVER (PARTITION BY actname) FROM act_gig NATURAL JOIN act WHERE DetermineHeadliner(act_gig.gigid, act_gig.actid) ORDER BY act.actid ASC, year DESC) SELECT v.actname, v.actid, v.gigid, v.year, v.count, ticket.customername, count(ticket.customername) OVER (PARTITION BY ticket.customername, v.actid) AS people FROM v LEFT OUTER JOIN ticket ON v.gigid = ticket.gigid) SELECT w.actname, w.actid, w.gigid, w.year, w.count, w.customername, w.people FROM w WHERE w.people >= w.count OR w.customername IS NULL ORDER BY people DESC) SELECT DISTINCT u.actname, u.customername FROM u ORDER BY u.actname ASC";
        try {
            Statement query = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet rs = query.executeQuery(q);
            //Checks the query has a valid result and also sets the pointer to the last row in the result set
            if(!rs.last()) {
                rs.close();
                throw new SQLException();
            }
            //Uses .getRow() to get the number of rows in the result set
            String[][] returnString = new String[rs.getRow()][2];
            rs.first();
            int count = 0;
            String index2insert;
            do {
                returnString[count][0] = rs.getString(1);
                index2insert = rs.getString(2);
                //The rows with [None] in the sample output are [NULL] in the query, so update the output to match the sample.
                if(index2insert == null) {
                    index2insert = "[None]";
                }
                returnString[count][1] = index2insert;
                count++;
            } while (rs.next());
            rs.close();
            return returnString;

        }catch(SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static String[][] task8(Connection conn){
        /*
        SELECT venuename, actname, numtickets 
        FROM (
            SELECT venue.venuename, act.actname, NumTickets(act.actid, venue.venueid), venue.capacity 
            FROM venue CROSS JOIN act
        ) AS v 
        WHERE v.numtickets < v.capacity 
        ORDER BY v.venuename ASC, v.numtickets DESC
         */
        String queryString = "SELECT venuename, actname, numtickets FROM (SELECT venue.venuename, act.actname, NumTickets(act.actid, venue.venueid), venue.capacity FROM venue CROSS JOIN act) AS v WHERE v.numtickets < v.capacity ORDER BY v.venuename ASC, v.numtickets DESC";
        try {
            Statement query = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet rs = query.executeQuery(queryString);
            if(!rs.last()) {
                rs.close();
                throw new SQLException();
            }
            //Creates the output string of size matching the size of the result set
            String[][] returnString = new String[rs.getRow()][3];
            rs.first();
            int count = 0;
            do {
                //Converts the result set to an array
                returnString[count][0] = rs.getString(1);
                returnString[count][1] = rs.getString(2);
                returnString[count][2] = Integer.toString(rs.getInt(3));
                count++;
            } while (rs.next());
            return returnString;

        }catch(SQLException e) {
            System.err.format("SQL State: %s\n%s\n", e.getSQLState(), e.getMessage());
            e.printStackTrace();
            return null;
        }
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