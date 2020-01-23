package edu.uw.cs;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.xml.bind.*;
import java.util.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
  // DB Connection
  private Connection conn;

  // current user of the app
  private String username;

  // Keep track of the most recent search itineraries
  ArrayList<Itinerary> itinResults;
  private int itinCount;

  // Current reservation ID (incremented each time one is used)
  private int resID = 1;

  // Keeps track of the flights associated with each reservation
  private Map<Integer, Itinerary> resDict = new HashMap<Integer, Itinerary>();

  // Password hashing parameter constants
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  // Canned queries
  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity, num_booked FROM Flights WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;
  
  // Empties Users db
  private static final String CLEAR_USER_DATA = "DELETE FROM Users";
  private PreparedStatement clearUserStatement;

  // Empties Reservations db
  private static final String CLEAR_RESERVATION_DATA = "DELETE FROM Reservations";
  private PreparedStatement clearReservationStatement; 

  // Enters user info into Users
  private static final String CREATE_LOGIN = "INSERT INTO Users VALUES (?, ?, ?)";
  private PreparedStatement createLoginStatement;

  // Checks if already logged in
  private static final String CHECK_LOGIN = "SELECT count(*) as cnt FROM Users WHERE username = ? AND password = ?";
  private PreparedStatement checkLoginStatement;  

  // Finds all direct flights for given info
  private static final String DIRECT_FLIGHT = "SELECT TOP (?) fid, day_of_month, carrier_id, flight_num, origin_city,"
                                              + "dest_city, actual_time, capacity, price FROM FLIGHTS " 
                                              + "WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? "
                                              + "AND cancelled = 0 ORDER BY actual_time ASC, fid ASC";
  private PreparedStatement directFlightStatement;

  // Finds all indirect flights for given info
  private static final String NON_DIRECT_FLIGHT = "SELECT TOP (?) "
                                                  + "F1.fid as F1_fid, F2.fid as F2_fid, F1.day_of_month as F1_day_of_month, F2.day_of_month as F2_day_of_month, "
                                                  + "F1.carrier_id as F1_carrier_id, F2.carrier_id as F2_carrier_id, F1.flight_num as F1_flight_num, F2.flight_num as F2_flight_num, "
                                                  + "F1.origin_city as F1_origin_city, F2.origin_city as F2_origin_city, F1.dest_city as F1_dest_city, F2.dest_city as F2_dest_city, "
                                                  + "F1.actual_time as F1_actual_time, F2.actual_time as F2_actual_time, F1.capacity as F1_capacity, F2.capacity as F2_capacity, "
                                                  + "F1.price as F1_price, F2.price as F2_price, F1.actual_time + F2.actual_time as total_time "
                                                  + "FROM FLIGHTS as F1, FLIGHTS as F2 "
                                                  + "WHERE F1.dest_city = F2.origin_city AND F1.origin_city = ? AND F2.dest_city = ? AND F1.day_of_month = ? "
                                                  + "AND F1.day_of_month = F2.day_of_month AND F1.cancelled = 0 AND F2.cancelled = 0 "
                                                  + "ORDER BY total_time ASC, F1.fid ASC, F2.fid ASC";
  private PreparedStatement nonDirectFlightStatement;

  // Finds number of reservations for a particular date
  private static final String RESERVATION_FOR_DAY = "SELECT count(*) as cnt FROM Reservations WHERE username = ? AND trip_date = ?";
  private PreparedStatement reservationForDayStatement;

  // Creates a reservation
  private static final String BOOK_ITIN = "INSERT INTO Reservations (rid, username, trip_date, fid1, fid2, cost) VALUES (?, ?, ?, ?, ?, ?)";
  private PreparedStatement bookItinStatement;

  // Updates number of taken seats for flight
  private static final String UPDATE_BOOKED_CAPACITY = "UPDATE Flights SET num_booked = num_booked + 1 WHERE fid = ?";
  private PreparedStatement updateBookedCapacityStatement;

  // Sets number of taken seats for flight back to 0
  private static final String RESET_BOOKED_CAPACITY = "UPDATE Flights SET num_booked = 0 WHERE num_booked > 0";
  private PreparedStatement resetBookedCapacityStatement;

  // Finds reservations for a given username
  private static final String CHECK_PAY_RESERVATION = "SELECT rid, paid FROM Reservations WHERE username = ?";
  private PreparedStatement checkPayReservationStatement;

  // Determines if a user paid for a reservation
  private static final String USER_PAID = "SELECT cost, paid FROM Reservations WHERE rid = ? AND username = ?";
  private PreparedStatement userPaidStatement;

  // Determines if a user cancelled a reservation
  private static final String USER_CANCELLED = "SELECT cost, cancelled FROM Reservations WHERE rid = ? AND username = ?";
  private PreparedStatement userCancelledStatement;

  // Finds balance of user
  private static final String USER_BALANCE = "SELECT balance FROM Users WHERE username = ?";
  private PreparedStatement userBalanceStatement;

  // Changes user's balance by certain amount
  private static final String USER_CHANGE_BALANCE = "UPDATE Users SET balance = balance - ? WHERE username = ?";
  private PreparedStatement userChangeBalanceStatement;  

  // Changes a reservation to be paid
  private static final String USER_PAY_RESERVATION = "UPDATE Reservations SET paid = 1 WHERE rid = ? AND username = ?";
  private PreparedStatement userPayReservationStatement;

  // Changes a reservation to be cancelled
  private static final String USER_CANCEL_RESERVATION = "UPDATE Reservations SET cancelled = 1 WHERE rid = ? AND username = ?";
  private PreparedStatement userCancelReservationStatement;

  /**
   * Establishes a new application-to-database connection. Uses the
   * dbconn.properties configuration settings
   * 
   * @throws IOException
   * @throws SQLException
   */
  public void openConnection() throws IOException, SQLException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("hw1.server_url");
    String dbName = configProps.getProperty("hw1.database_name");
    String adminName = configProps.getProperty("hw1.username");
    String password = configProps.getProperty("hw1.password");
    String connectionUrl = String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
        dbName, adminName, password);
    conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      resID = 1;
      clearUserStatement.clearParameters();
      clearReservationStatement.execute();  
      clearUserStatement.execute();
      resetBookedCapacityStatement.executeUpdate();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  public void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
    clearUserStatement = conn.prepareStatement(CLEAR_USER_DATA);
    clearReservationStatement = conn.prepareStatement(CLEAR_RESERVATION_DATA);
    createLoginStatement = conn.prepareStatement(CREATE_LOGIN);
    checkLoginStatement = conn.prepareStatement(CHECK_LOGIN);
    directFlightStatement = conn.prepareStatement(DIRECT_FLIGHT);
    nonDirectFlightStatement = conn.prepareStatement(NON_DIRECT_FLIGHT);
    reservationForDayStatement = conn.prepareStatement(RESERVATION_FOR_DAY);
    bookItinStatement = conn.prepareStatement(BOOK_ITIN);
    updateBookedCapacityStatement = conn.prepareStatement(UPDATE_BOOKED_CAPACITY);
    resetBookedCapacityStatement = conn.prepareStatement(RESET_BOOKED_CAPACITY);
    checkPayReservationStatement = conn.prepareStatement(CHECK_PAY_RESERVATION);
    userPaidStatement = conn.prepareStatement(USER_PAID);
    userCancelledStatement = conn.prepareStatement(USER_CANCELLED);
    userBalanceStatement = conn.prepareStatement(USER_BALANCE);
    userChangeBalanceStatement = conn.prepareStatement(USER_CHANGE_BALANCE);
    userPayReservationStatement = conn.prepareStatement(USER_PAY_RESERVATION);
    userCancelReservationStatement = conn.prepareStatement(USER_CANCEL_RESERVATION);
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged
   *         in\n" For all other errors, return "Login failed\n". Otherwise,
   *         return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {
    try {
      if (this.username != null)
        return "User already logged in\n";
      checkLoginStatement.clearParameters();
      checkLoginStatement.setString(1, username);
      checkLoginStatement.setString(2, password);

      ResultSet result = checkLoginStatement.executeQuery();
      result.next();
      int cnt = result.getInt("cnt");
      result.close();
      if (cnt == 1) {
        this.username = username;
        itinResults = null;
        itinCount = 0;
        return "Logged in as " + username + "\n";
      }
    } catch (Exception e) {
      return "Login failed\n";
    }
    return "Login failed\n";
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should
   *                   be >= 0 (failure otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n"
   *         if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    try {
      if (initAmount < 0) {
        return "Failed to create user\n";
      }
      createLoginStatement.clearParameters();  // Creates a user
      createLoginStatement.setString(1, username);
      createLoginStatement.setString(2, password);
      createLoginStatement.setInt(3, initAmount);
      createLoginStatement.execute();
      return "Created user " + username + "\n"; 
    } catch (Exception e) {
      return "Failed to create user\n";
    }
  }

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination
   * city, on the given day of the month. If {@code directFlight} is true, it only
   * searches for direct flights, otherwise is searches for direct flights and
   * flights with two "hops." Only searches for up to the number of itineraries
   * given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights,
   *                            otherwise include indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your
   *         selection\n". If an error occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total
   *         flight time] minutes\n [first flight in itinerary]\n ... [last flight
   *         in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the
   *         {@code Flight} class. Itinerary numbers in each search should always
   *         start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth,
      int numberOfItineraries) {
    String finalResult = "";

    try {
      directFlightStatement.clearParameters();  // Finds direct flights
      directFlightStatement.setInt(1, numberOfItineraries);
      directFlightStatement.setString(2, originCity);
      directFlightStatement.setString(3, destinationCity);
      directFlightStatement.setInt(4, dayOfMonth);
      
      itinResults = new ArrayList<Itinerary>();
      ResultSet result = directFlightStatement.executeQuery();
      int itinCurr = 0;
      while (result.next()) {  // Adds all(up to n) direct flights to list of itineraries
        itinResults.add(new Itinerary(new Flight(result, "")));
        itinCurr += 1;
      }

      result.close();

      if (directFlight == false) {
        nonDirectFlightStatement.clearParameters();  // Finds non-direct flights
        nonDirectFlightStatement.setInt(1, numberOfItineraries - itinCurr);
        nonDirectFlightStatement.setString(2, originCity);  
        nonDirectFlightStatement.setString(3, destinationCity);
        nonDirectFlightStatement.setInt(4, dayOfMonth);

        ResultSet nonDirectResult = nonDirectFlightStatement.executeQuery();
        while (nonDirectResult.next()) {  // Adds rest (n-k) of itineraries with non-direct flights
          itinResults.add(new Itinerary(new Flight(nonDirectResult, "F1_"), new Flight(nonDirectResult, "F2_")));
          itinCurr += 1;
        }

        nonDirectResult.close();
      }

      Collections.sort(itinResults);  // Sorts the list of iteneraries by time

      for (int i = 0; i < itinCurr; i++) {
        finalResult += "Itinerary " + i + ": ";
        finalResult += itinResults.get(i).toString();
      }

      itinCount = itinCurr;
      return finalResult;
    } catch (Exception e) {
      return "Failed to search\n";
    }
  }


  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is
   *                    returned by search in the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations,
   *         not logged in\n". If try to book an itinerary with invalid ID, then
   *         return "No such itinerary {@code itineraryId}\n". If the user already
   *         has a reservation on the same day as the one that they are trying to
   *         book now, then return "You cannot book two flights in the same
   *         day\n". For all other errors, return "Booking failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID:
   *         [reservationId]\n" where reservationId is a unique number in the
   *         reservation system that starts from 1 and increments by 1 each time a
   *         successful reservation is made by any user in the system.
   */
  public String transaction_book(int itineraryId) {
    if (username == null) {
      return "Cannot book reservations, not logged in\n";
    }

    if (itinCount <= itineraryId || itineraryId < 0) {  // Error if invalid itinerary ID
      return "No such itinerary " + itineraryId + "\n";
    }

    Itinerary currentItin = itinResults.get(itineraryId);
    try {
      reservationForDayStatement.clearParameters();  // Checks how many reservations for specific date
      reservationForDayStatement.setString(1, username);
      reservationForDayStatement.setInt(2, currentItin.flight1.dayOfMonth);

      ResultSet reservations = reservationForDayStatement.executeQuery();
      reservations.next();
      int cnt = reservations.getInt("cnt");
      reservations.close();
      if (cnt != 0)
        return "You cannot book two flights in the same day\n";

      boolean seatsLeft = checkFlightCapacity(currentItin.flight1.fid) > 0;
      if (seatsLeft && currentItin.direct == false)
        seatsLeft = checkFlightCapacity(currentItin.flight2.fid) > 0;

      if (seatsLeft) {  // Continues with booking if there are seats left
        updateBookedCapacity(currentItin.flight1.fid);
        if(currentItin.direct == false)
          updateBookedCapacity(currentItin.flight2.fid);

        double cost;
        if (currentItin.direct) {  // Sets second flight to NULL if it is a direct flight
          cost = currentItin.flight1.price;
          bookItinStatement.clearParameters();  // Creates Booking
          bookItinStatement.setInt(1, resID);
          bookItinStatement.setString(2, username);
          bookItinStatement.setInt(3, currentItin.flight1.dayOfMonth);
          bookItinStatement.setInt(4, currentItin.flight1.fid);
          bookItinStatement.setNull(5, Types.INTEGER);
          bookItinStatement.setDouble(6, cost);
          bookItinStatement.execute();
        }
        else {  // Sets second flight to actual ID if non-direct flight
          cost = currentItin.flight1.price + currentItin.flight2.price;
          bookItinStatement.clearParameters();  // Creates Booking
          bookItinStatement.setInt(1, resID);
          bookItinStatement.setString(2, username);
          bookItinStatement.setInt(3, currentItin.flight1.dayOfMonth);
          bookItinStatement.setInt(4, currentItin.flight1.fid);
          bookItinStatement.setInt(5, currentItin.flight2.fid);
          bookItinStatement.setDouble(6, cost);
          bookItinStatement.execute();
        }

        resDict.put(resID, currentItin);  // Associates current itinerary with given reservation ID

        int resIDTemp = resID;
        resID += 1;  // Gets next reservation ID ready for use
        return "Booked flight(s), reservation ID: " + resIDTemp + "\n";
      }
      else
        return "Booking failed\n";
    } catch(Exception e) {
      return "Booking failed\n";
    }
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n"
   *         If the reservation is not found / not under the logged in user's
   *         name, then return "Cannot find unpaid reservation [reservationId]
   *         under user: [username]\n" If the user does not have enough money in
   *         their account, then return "User has only [balance] in account but
   *         itinerary costs [cost]\n" For all other errors, return "Failed to pay
   *         for reservation [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining
   *         balance: [balance]\n" where [balance] is the remaining balance in the
   *         user's account.
   */
  public String transaction_pay(int reservationId) {
    if (username == null)
      return "Cannot pay, not logged in\n";

    try {
      userPaidStatement.clearParameters();  // Checks if user paid and if not how much owed
      userPaidStatement.setInt(1, reservationId);
      userPaidStatement.setString(2, username);

      ResultSet owed = userPaidStatement.executeQuery();
      int costOfRes;
      if (!owed.next()) // different user's reservation
        return "Cannot find unpaid reservation " + reservationId + " under user: " + username + "\n";
      else if (owed.getInt("paid") != 0) // already paid for
        return "Cannot find unpaid reservation " + reservationId + " under user: " + username + "\n";
      else
        costOfRes = owed.getInt("cost");
      owed.close();

      userBalanceStatement.clearParameters();  // Gets user's balance
      userBalanceStatement.setString(1, username);

      ResultSet userBal = userBalanceStatement.executeQuery();
      userBal.next();
      int bal = userBal.getInt("balance");
      userBal.close();

      if (bal < costOfRes)
        return "User has only " + bal + " in account but itinerary costs " + costOfRes + "\n";

      userChangeBalanceStatement.clearParameters();  // Reduces user's balance
      userChangeBalanceStatement.setInt(1, costOfRes);
      userChangeBalanceStatement.setString(2, username);
      userChangeBalanceStatement.executeUpdate();

      userPayReservationStatement.clearParameters();  // Changes reservation to be "paid"
      userPayReservationStatement.setInt(1, reservationId);
      userPayReservationStatement.setString(2, username);
      userPayReservationStatement.executeUpdate();

      return "Paid reservation: " + reservationId + " remaining balance: " + (bal - costOfRes) + "\n";
    } catch(Exception e) {
      return "Failed to pay for reservation " + reservationId + "\n";
    }
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not
   *         logged in\n" If the user has no reservations, then return "No
   *         reservations found\n" For all other errors, return "Failed to
   *         retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n" [flight 1
   *         under the reservation] [flight 2 under the reservation] Reservation
   *         [reservation ID] paid: [true or false]:\n" [flight 1 under the
   *         reservation] [flight 2 under the reservation] ...
   *
   *         Each flight should be printed using the same format as in the
   *         {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    if (username == null)
      return "Cannot view reservations, not logged in\n";

    try {
      checkPayReservationStatement.clearParameters();  // Finds all reservations for user
      checkPayReservationStatement.setString(1, username);
      ResultSet results = checkPayReservationStatement.executeQuery();

      int resCnt = 0;
      String finalResult = "";
      while(results.next()) {  // Prints out reservations
        int rid = results.getInt("rid");
        if (resDict.get(rid) != null) {  // Doesn't print cancelled reservations
          Itinerary itin = resDict.get(rid);
          finalResult += "Reservation " + rid + " paid: ";
          finalResult += results.getInt("paid") == 0 ? "false:\n" : "true:\n";
          finalResult += itin.flight1.toString() + "\n";
          if (!itin.direct)
            finalResult += itin.flight2.toString() + "\n";
          resCnt++;
        }
      }

      if (resCnt == 0)
        return "No reservations found\n";

      return finalResult;
    } catch(Exception e) {
      return "Failed to retrieve reservations\n";
    }
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations,
   *         not logged in\n" For all other errors, return "Failed to cancel
   *         reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be
   *         reused by the system.
   */
  public String transaction_cancel(int reservationId) {
    //remove from resDict
    if (username == null)
      return "Cannot cancel reservations, not logged in\n";

    try {
      userCancelledStatement.clearParameters();  // Checks if user cancelled and how much should be refunded if paid for
      userCancelledStatement.setInt(1, reservationId);
      userCancelledStatement.setString(2, username);

      ResultSet cancelled = userCancelledStatement.executeQuery();
      int refund;
      if (!cancelled.next()) // different user's reservation
        return "Failed to cancel reservation " + reservationId + "\n";
      else if (cancelled.getInt("cancelled") != 0) // already cancelled
        return "Failed to cancel reservation " + reservationId + "\n";
      else
        refund = cancelled.getInt("cost") * -1;
      cancelled.close();

      userBalanceStatement.clearParameters();  // Gets user's balance
      userBalanceStatement.setString(1, username);

      ResultSet userBal = userBalanceStatement.executeQuery();
      userBal.next();
      int bal = userBal.getInt("balance");
      userBal.close();

      userPaidStatement.clearParameters();  // Checks if user paid
      userPaidStatement.setInt(1, reservationId);
      userPaidStatement.setString(2, username);

      ResultSet paidFor = userPaidStatement.executeQuery();
      paidFor.next();
      int paidRes = paidFor.getInt("paid");
      paidFor.close();
      if (paidRes == 1) {
        userChangeBalanceStatement.clearParameters();  // Changes balance of user to reflect refund if paid for
        userChangeBalanceStatement.setInt(1, refund);
        userChangeBalanceStatement.setString(2, username);
        userChangeBalanceStatement.executeUpdate();
      }

      userCancelReservationStatement.clearParameters();  // Changes reservation to cancelled
      userCancelReservationStatement.setInt(1, reservationId);
      userCancelReservationStatement.setString(2, username);
      userCancelReservationStatement.executeUpdate();

      resDict.remove(reservationId);
      return "Canceled reservation " + reservationId + "\n";
    } catch(Exception e) {
      e.printStackTrace();
      return "Failed to cancel reservation " + reservationId + "\n";
    }
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    int num_booked = results.getInt("num_booked");
    results.close();

    return capacity - num_booked;
  }

  // Increments a flight's number of booked seats 
  private void updateBookedCapacity(int fid) throws SQLException {
    updateBookedCapacityStatement.clearParameters();
    updateBookedCapacityStatement.setInt(1, fid);
    updateBookedCapacityStatement.executeUpdate();
  }

  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    public Flight(int fid, int dayOfMonth, String carrierId, String flightNum, String originCity,
                  String destCity, int time, int capacity, int price)
    {
      this.fid = fid;
      this.dayOfMonth = dayOfMonth;
      this.carrierId = carrierId;
      this.flightNum = flightNum;
      this.originCity = originCity;
      this.destCity = destCity;
      this.time = time;
      this.capacity = capacity;
      this.price = price;
    }

    public Flight(ResultSet r, String identifier) throws SQLException
    {
      this.fid = r.getInt(identifier + "fid");
      this.dayOfMonth = r.getInt(identifier + "day_of_month");
      this.carrierId = r.getString(identifier + "carrier_id");
      this.flightNum = r.getString(identifier + "flight_num");
      this.originCity = r.getString(identifier + "origin_city");
      this.destCity = r.getString(identifier + "dest_city");
      this.time = r.getInt(identifier + "actual_time");
      this.capacity = r.getInt(identifier + "capacity");
      this.price = r.getInt(identifier + "price");
    }

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: " + flightNum + " Origin: "
          + originCity + " Dest: " + destCity + " Duration: " + time + " Capacity: " + capacity + " Price: " + price;
    }
  }

  public class Itinerary implements Comparable<Itinerary> {
    public Flight flight1;
    public Flight flight2;
    public boolean direct;
    public int totalTime;

    public Itinerary(Flight flight) {
      flight1 = flight;
      flight2 = null;
      direct = true;
      totalTime = flight.time;
    }

    public Itinerary(Flight flight1, Flight flight2) {
      this.flight1 = flight1;
      this.flight2 = flight2;
      direct = false;
      totalTime = flight1.time + flight2.time;
    }

    public int compareTo(Itinerary itin) {  // To sort the itineraries
      if (this.totalTime == itin.totalTime)
        return 0;
      else if (this.totalTime > itin.totalTime)
        return 1;
      else
        return -1;
    }

    @Override
    public String toString() {
      int flightCount;
      String flight2Description = "";
      if (direct)
        flightCount = 1;
      else {
        flightCount = 2;
        flight2Description += flight2.toString() + "\n";
      }
      return flightCount + " flight(s), " + totalTime + " minutes\n" + flight1.toString() + "\n" + flight2Description;
    }
  }
}
