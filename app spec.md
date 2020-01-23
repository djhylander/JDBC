# CSE 414 Homework 6: Database Application Management

**Objectives:**
To gain experience with database application development.
To learn how to use SQL from within Java via JDBC.

**Assignment tools:**
* [SQL Server](http://www.microsoft.com/sqlserver) through [SQL Azure](https://azure.microsoft.com/en-us/services/sql-database/)
* Maven (if using OSX, we recommend using Homebrew and installing with `brew install maven`)
* [Prepared Statements](https://docs.oracle.com/javase/8/docs/api/java/sql/PreparedStatement.html)
* starter code files

Instructions assume you are on the Linux lab machines, attu, or home VM.


**Assigned date:** Nov 5, 2019

**Due date:** Wednesday Nov 12, 2019, at 11:00pm. You have 1 week for this assignment.

***Warning: This is more code than you've written for other 414 assignments. You will use your code from this assignment for the next assignment as well. Plan accordingly.***

**What to turn in:**

* Customer database schema in `createTables.sql`,
* a copy of the starter code including your completed version of the `Query.java`,
* the test cases that you created with a descriptive name for each case,
* and a write up of your design.

We will be testing your implementations using the home VM.


## Assignment Details

**Read this whole document before starting this project.** There is a lot of valuable information here, and some implementation details depend on others.

Congratulations, you are opening your own flight booking service!
In this homework, you have two main tasks:
* Design a database of your customers and the flights they book.
* Complete a working prototype of your flight booking application that connects to the database then allows customers to use a CLI to search, book, cancel, etc. flights.

You will also be writing a few test cases and explaining your implementation in a short writeup. We have already provided code for a UI and partial backend; you will implement the rest of the backend. In real life, you would develop a web-based interface instead of a CLI, but we use a CLI to simplify this homework.

For this lab, you can use any of the classes from the Java 8 standard JDK.

#### Connect your application to your database
You will need to access your Flights database on SQL Azure from HW3. Alternatively, you may create a new database and use the HW3 specification for importing Flights data.

##### Configure your JDBC Connection
Edit the file dbconn.properties with the appropriate information to connect Query.java to your new database.
* The server URL will be of the form your_server_name.database.windows.net
* The database name, admin, and password will be whatever you specified
* If the connection isnt working for some reason, try using the fully qualified username:
hw1.username = USER_NAME@SERVER_NAME

Use a fake username and password for dbconn.properties or delete dbconn.properties before turning in your implementation.

##### Build the application
We will use the Java build tool Maven to handle building our project from source code to runnable file.

Make sure your application can run by entering the following commands in the directory of the starter code and pom.xml file. This first command will package the application files and any dependencies into a single .jar file:

```mvn clean compile assembly:single```

This second command will run the main method from FlightService.java, the interface logic for what you will implement in Query.java:

```java -jar target/flightapp-1.0-jar-with-dependencies.jar```

If you get our UI below, you are good to go for the rest of the lab!

```
*** Please enter one of the following commands ***
> create <username> <password> <initial amount>
> login <username> <password>
> search <origin city> <destination city> <direct> <day> <num itineraries>
> book <itinerary id>
> pay <reservation id>
> reservations
> cancel <reservation id>
> quit
```

#### Data Model

The flight service system consists of the following logical entities.
These entities are *not necessarily database tables*. 
It is up to you to decide what entities to store persistently and create a physical schema design that has the ability to run the operations below, which make use of these entities.

- **Flights / Carriers / Months / Weekdays**: modeled the same way as HW3.  For this application, we have very limited functionality so you shouldn't  modify the schema from HW3 nor add any new tables to reason about the data. Do not change the flights table in your code.

- **Users**: A user has a username (`varchar`), password (`varbinary`), and balance (`int`) in their account. All usernames should be unique in the system. Each user can have any number of reservations. Usernames are case insensitive (this is the default for SQL Server). Since we are salting and hashing our passwords through the Java application, passwords are case sensitive. You can assume that all usernames and passwords have at most 20 characters.

- **Itineraries**: An itinerary is either a direct flight (consisting of one flight: origin --> destination) or a one-hop flight (consiting of two flights: origin --> stopover city, stopover city --> destination). Itineraries are returned by the search command.

- **Reservations**: A booking for an itinerary, which may consist of one (direct) or two (one-hop) flights.
Each reservation can either be paid or unpaid, cancelled or not, and has a unique ID.

Create other tables or indexes you need for this assignment in `createTables.sql` (see below).


#### Requirements
The following are the functional specifications for the flight service system, to be implemented in `Query.java` (see the method stubs in the starter code for full specification as to what error message to return, etc):

- **create** takes in a new username, password, and initial account balance as input. It creates a new user account with the initial balance. It should return an error if negative, or if the username already exists. Usernames and passwords are checked case-insensitively. You can assume that all usernames and passwords have at most 20 characters. You do not have to use hashing and salting passwords for this assignment, and can store the plain text.

- **login** takes in a username and password, and checks that the user exists in the database and that the password matches. You can use a plaintext comparison against their password to check that it matches. 

  Within a single session (that is, a single instance of your program), only one user should be logged in.  You can track this via a local variable in your program.  If a second login attempt is made, please return "User already logged in".  Across multiple sessions (that is, if you run your program multiple times), the same user is allowed to be logged in.  This means that you do not need to track a user's login status inside the database.

- **search** takes as input an origin city (string), a destination city (string), a flag for only direct flights or not (0 or 1), the date (int), and the maximum number of itineraries to be returned (int). For the date, we only need the day of the month, since our dataset comes from July 2015.
Return only flights that are not canceled, ignoring the capacity and number of seats available. If the user requests n itineraries to be returned, there are a number of possibilities:
    * direct=1: return up to n direct itineraries
    * direct=0: return up to n direct itineraries. If there are k direct itineraries (where k < n), then return the k direct itineraries and then return up to (n-k) of the shortest indirect itineraries with the flight times.
For one-hop flights, different carriers can be used for the flights. For the purpose of this assignment, an indirect itinerary means the first and second flight only must be on the same date (i.e., if flight 1 runs on the 3rd day of July, flight 2 runs on the 4th day of July, then you can't put these two flights in the same itinerary as they are not on the same day).


    Sort your results. In all cases, the returned results should be primarily sorted on total actual_time (ascending). If a tie occurs, break that tie by the fid value. Use the first then the second fid for tie-breaking.

    Below is an example of a single direct flight from Seattle to Boston. Actual itinerary numbers might differ, notice that only the day is printed out since we assume all flights happen in July 2015:

    ```
    Itinerary 0: 1 flight(s), 297 minutes 
    ID: 60454 Day: 1 Carrier: AS Number: 24 Origin: Seattle WA Dest: Boston MA Duration: 297 Capacity: 14 Price: 140
    ```

    Below is an example of two indirect flights from Seattle to Boston:

    ```
    Itinerary 0: 2 flight(s), 317 minutes 
    ID: 704749 Day: 10 Carrier: AS Number: 16 Origin: Seattle WA Dest: Orlando FL Duration: 159 Capacity: 10 Price: 494 
    ID: 726309 Day: 10 Carrier: B6 Number: 152 Origin: Orlando FL Dest: Boston MA Duration: 158 Capacity: 0 Price: 104 
    Itinerary 1: 2 flight(s), 317 minutes 
    ID: 704749 Day: 10 Carrier: AS Number: 16 Origin: Seattle WA Dest: Orlando FL Duration: 159 Capacity: 10 Price: 494 
    ID: 726464 Day: 10 Carrier: B6 Number: 452 Origin: Orlando FL Dest: Boston MA Duration: 158 Capacity: 7 Price: 760
    ```

    Note that for one-hop flights, the results are printed in the order of the itinerary, starting from the flight leaving the origin and ending with the flight arriving at the destination.

    The returned itineraries should start from 0 and increase by 1 up to n as shown above. If no itineraries match the search query, the system should return an informative error message. See Query.java for the actual text.

    The user need not be logged in to search for flights.

    All flights in an indirect itinerary should be under the same itinerary ID. In other words, the user should only need to book once with the itinerary ID for direct or indirect trips.

- **book** lets a user book an itinerary by providing the itinerary number as returned by a previous search. 
  The user must be logged in to book an itinerary, and must enter a valid itinerary id that was returned in the last search that was performed *within the same login session*. Make sure you make the corresponding changes to the tables in case of a successful booking. Once the user logs out (by quitting the application), logs in (if they previously were not logged in), or performs another search within the same login session, then all previously returned itineraries are invalidated and cannot be booked. 
  
  A user cannot book a flight if the flight's maximum capacity would be exceeded. Each flight’s capacity is stored in the Flights table as in HW3, and you should have records as to how many seats remain on each flight based on the reservations.

  If booking is successful, then assign a new reservation ID to the booked itinerary. Note that 1) each reservation can contain up to 2 flights (in the case of indirect flights), and 2) each reservation should have a unique ID that incrementally increases by 1 for each successful booking.


- **pay** allows a user to pay for an existing unpaid reservation. 
  It first checks whether the user has enough money to pay for all the flights in the given reservation. If successful, it updates the reservation to be paid.


- **reservations** lists all reservations for the user. 
  Each reservation must have ***a unique identifier (which is different for each itinerary) in the entire system***, starting from 1 and increasing by 1 after a reservation has been made. 
  
    There are many ways to implement this. One possibility is to define a "ID" table that stores the next ID to use, and update it each time when a new reservation is made successfully.
  
    The user must be logged in to view reservations. The itineraries should be displayed using similar format as that used to display the search results, and they should be shown in increasing order of reservation ID under that username. Cancelled reservations should not be displayed.


- **cancel** lets a user to cancel an existing uncanceled reservation. The user must be logged in to cancel reservations and must provide a valid reservation ID. Make sure you make the corresponding changes to the tables in case of a successful cancellation (e.g., if a reservation is already paid, then the customer should be refunded).


- **quit** leaves the interactive system and logs out the current user (if logged in).


Refer to the Javadoc in `Query.java` for full specification and the expected responses of the commands above. 

**Make sure your code produces outputs in the same formats as prescribed! (see test cases and javadoc for what to expect)**


### Task 1: Customer database design (10 points)

Your first task is to design and add tables to your flights database. You should decide on the relational tables given the logical data model described above. You can add other tables to your database as well.

**What to turn in**: a single text file called `createTables.sql` with `CREATE TABLE` and any `INSERT` statements (and optionally any `CREATE INDEX` statements) needed to implement the logical data model above. We will test your implementation with the flights table populated with HW3 data using the schema above, and then running your `createTables.sql`. So make sure your file is runnable on SQL Azure through SQL Server Management Studio or their web interface. 

You may want to write a separate script file with `DROP TABLE` or `DELETE FROM` statements; 
it's useful to run it whenever you find a bug in your schema or data. You don't need to turn in anything for this.
 

### Task 2: Java customer application (70 points)

Your second task is to write the Java application that your customers will use, by completing the starter code. You need to modify only `Query.java`. Do not modify `FlightService.java`.

For this homework, we are only concerned about the correctness of your methods. You do not need to implement any parallelization/transactions... yet. We expect that you use prepared statements where applicable. Please make your code reasonably easy to read.

To keep things neat we have provided you with the `Flight` inner class that acts as a container for your flight data. The `toString` method in the Flight class matches what is needed in methods like search. We have also provided a sample helper method `checkFlightCapacity` that uses a prepared statement. `checkFlightCapacity` outlines the way we think forming prepared statements should go for this assignment (creating a constant SQL string, preparing it in the prepareStatements method, and then finally using it).


Below is our **suggested approach** to tackling this application:

#### Step a: Implement `clearTables`

Implement this method in `Query.java` to clear the contents of any tables you have created for this assignment (e.g., reservations). However, do not drop any of them and do not delete the contents or drop the `flights` table . 

After calling this method the database should be in the same state as the beginning, i.e., with the flights table populated and `createTables.sql` called. This method is for running the test harness where each test case is assumed to start with a clean database. You will see how this works after running the test harness.

**`clearTables` should not take more than a minute.** Make sure your database schema is designed with this in mind.

#### Step b: Implement create, login, and search
#### Step c: Implement book, pay, reservations, and cancel

### Task 3: Write test cases (10 points)
While we provide a testing framework for most of your methods, the testing we provide is partial (although significant). It is up to you to implement your solutions so that they completely follow the provided specification.

To test that your queries work correctly, we have provided a test harness using the JUnit framework. Our test harness will compile your code and run the test cases in the folder you provided. To run the harness, execute in the submission folder:

```mvn test```

For every test case it will either print pass or fail, and for all failed cases it will dump out what the implementation returned, and you can compare it with the expected output in the corresponding case file.

Each test case file is of the following format:

```sh
[command 1]
[command 2]
...
* 
[expected output line 1]
[expected output line 2]
...
*
# everything following ‘#’ is a comment on the same line
```

Your task is to write **at least 1 test case for each of the 7 commands** (you don't need to test `quit`). Separate each test case in its own file and name it `<command name>_<some descriptive name for the test case>.txt` and turn them in. It’s fine to turn in test cases for erroneous conditions (e.g., booking on a full flight, logging in with a non-existent username).

**What to turn in:** One file per test case in your `submission\cases` directory.

### 4. Writeup (10 points)
Please describe and/or draw your database design. This is so we can understand your implementation as close to what you were thinking. Explain your design choices in creating new tables. Also, describe your thought process in deciding what needs to be persisted on the database and what can be implemented in-memory (not persisted on the database). Please be concise in your writeup (< half a page).

**What to turn in:** Name this file `writeup.pdf` and add to your submission directory.



## Submission Instructions

 Add your Java code to `Query.java`. Add `create_tables.sql` and `writeup.pdf`. Add your test files to the cases directory.

**Important**: To remind you, in order for your answers to be added to the git repo, 
you need to explicitly add each file:

```sh
$ git add create_tables.sql ...
```

and push to make sure your code is uploaded to GitLab:

```sh
$ git commit
$ git push
```

**Again, just because your code has been committed on your local machine does not mean that it has been 
submitted -- it needs to be on GitLab!**

Make sure you check the results afterwards to make sure that your files
have been committed.
