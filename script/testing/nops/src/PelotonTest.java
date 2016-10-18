//===----------------------------------------------------------------------===//
//
//                         Peloton
//
// PelotonTest.java
//
// Identification: script/testing/nops/src/PelotonTest.java
//
// Copyright (c) 2015-16, Carnegie Mellon University Database Group
//
//===----------------------------------------------------------------------===//

import java.sql.*;
import java.util.Arrays;
import java.util.Random;
import java.util.Date;
import java.util.Properties;

public class PelotonTest {
  // Peloton, Postgres, Timesten
  private final String[] url = {
     "jdbc:postgresql://localhost:54321/postgres",  // PELOTON 
     "jdbc:postgresql://localhost:57721/postgres",   // POSTGRES
     "jdbc:timesten:client:TTC_SERVER_DSN=xxx;TTC_SERVER=xxx;TCP_PORT=xxx"}; // TIMESTEN

  private final String[] user = {"postgres", "postgres", "xxx"};
  private final String[] pass = {"postgres", "postgres", "xxx"};
  private final String[] driver = {"org.postgresql.Driver", "org.postgresql.Driver", "com.timesten.jdbc.TimesTenDriver"};
  
  private final int LOG_LEVEL = 0;
  private int query_type = SIMPLE_SELECT;  

  public static final int SEMICOLON = 0;
  public static final int SIMPLE_SELECT = 1;

  private final String DROP = "DROP TABLE IF EXISTS A;" +
          "DROP TABLE IF EXISTS B;";
  private final String DDL = "CREATE TABLE A (id INT PRIMARY KEY, data TEXT);";

  private final String DATA_A = "1,'1961-06-16'";
  private final String DATA_B = "2,'Full Clip'";
  private final String INSERT_A = "INSERT INTO A VALUES ("+ DATA_A +");";
  private final String INSERT_B = "INSERT INTO A VALUES ("+ DATA_B +")";

  private final String INSERT_A_1 = "INSERT INTO A VALUES ("+ DATA_A +");";
  private final String INSERT_A_2 = "INSERT INTO A VALUES ("+ DATA_B +")";
  private final String DELETE_A = "DELETE FROM A WHERE id=1";

  private final String AGG_COUNT = "SELECT COUNT(*) FROM A";
  private final String AGG_COUNT_2 = "SELECT COUNT(*) FROM A WHERE id = 1";
  private final String AGG_COUNT_3 = "SELECT AVG(id) FROM A WHERE id < ? + 1";

  private final String TEMPLATE_FOR_BATCH_INSERT = "INSERT INTO A VALUES (?,?);";

  private final String INSERT = "BEGIN;" +
          "INSERT INTO A VALUES (1,'Fly High');" +
          "COMMIT;";

  private final String SEQSCAN = "SELECT * FROM A";
  private final String INDEXSCAN = "SELECT * FROM A WHERE id = 1";
  private final String INDEXSCAN_COLUMN = "SELECT data FROM A WHERE id = 1";
  private final String INDEXSCAN_PARAM = "SELECT * FROM A WHERE id = ?";
  private final String BITMAPSCAN = "SELECT * FROM A WHERE id > ? and id < ?";
  private final String UPDATE_BY_INDEXSCAN = "UPDATE A SET data=? WHERE id=?";
  private final String UPDATE_BY_SCANSCAN = "UPDATE A SET data='YO'";
  private final String DELETE_BY_INDEXSCAN = "DELETE FROM A WHERE id = ?";
  private final String SELECT_FOR_UPDATE = "SELECT * FROM A WHERE id = 1 FOR UPDATE";
  private final String UNION = "SELECT * FROM A WHERE id = ? UNION SELECT * FROM B WHERE id = ?";
  
  // To test the join in TPCC
  private final String CREATE_STOCK_TABLE = "CREATE TABLE STOCK ("
        + "S_W_ID INT PRIMARY KEY,"
        + "S_I_ID INT PRIMARY KEY,"
        + "S_QUANTITY DECIMAL NOT NULL);";
        //+ "PRIMARY KEY (S_W_ID, S_I_ID));";
  private final String CREATE_ORDER_LINE_TABLE = "CREATE TABLE ORDER_LINE ("
        + "OL_W_ID INT NOT NULL PRIMARY KEY,"
        + "OL_D_ID INT NOT NULL PRIMARY KEY,"
        + "OL_O_ID INT NOT NULL PRIMARY KEY,"
        + "OL_NUMBER INT NOT NULL PRIMARY KEY,"
        + "OL_I_ID INT NOT NULL);";
        //+ "PRIMARY KEY (OL_W_ID,OL_D_ID,OL_O_ID,OL_NUMBER));";

  private final String INSERT_STOCK_1 = "INSERT INTO STOCK VALUES (1, 2, 0);";
  private final String INSERT_STOCK_2 = "INSERT INTO STOCK VALUES (1, 5, 1);";
  private final String INSERT_STOCK_3 = "INSERT INTO STOCK VALUES (1, 7, 6);";
  private final String SELECT_STOCK = "SELECT * FROM STOCK;";
  // Test general expression evaluation
  private final String SELECT_STOCK_COMPLEX = "SELECT * FROM STOCK WHERE"
      + " S_W_ID + S_I_ID = ? + S_QUANTITY + 1;";
  
  private final String INSERT_ORDER_LINE = "INSERT INTO ORDER_LINE VALUES (1, 2, 3, 4, 5);";
  private final String STOCK_LEVEL = "SELECT COUNT(DISTINCT (S_I_ID)) AS STOCK_COUNT"
      + " FROM " + "ORDER_LINE, STOCK"
      //+ " FROM " + "ORDER_LINE JOIN STOCK on S_I_ID = OL_I_ID"
      + " WHERE OL_W_ID = ?"
      + " AND OL_D_ID = ?"
      + " AND OL_O_ID < ?"
      + " AND OL_O_ID >= ? - 20"
      + " AND S_W_ID = ?"
      + " AND S_I_ID = OL_I_ID"
      + " AND S_QUANTITY < ?";

  private final String CREATE_TIMESTAMP_TABLE = "CREATE TABLE TS ("
      + "id INT NOT NULL,"
      + "ts TIMESTAMP NOT NULL);";
  private final String INSERT_TIMESTAMP = "INSERT INTO TS VALUES (1, ?);";

  private final Connection conn;

  private enum TABLE {A, B, AB}

  private int target; 

  public PelotonTest(int target) throws SQLException {
    this.target = target;
    try {
      Class.forName(driver[target]);
      if (LOG_LEVEL != 0) {
        org.postgresql.Driver.setLogLevel(LOG_LEVEL);
        DriverManager.setLogStream(System.out);
      }
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    conn = this.makeConnection();
    return;
  }

  private Connection makeConnection() throws SQLException {
    Connection conn = DriverManager.getConnection(url[target], user[target], pass[target]);
    return conn;
  }

  public void Close() throws SQLException {
    conn.close();
  }

  /**
   * Drop if exists and create testing database
   *
   * @throws SQLException
   */
  public void Init() throws SQLException {
    conn.setAutoCommit(true);
    Statement stmt = conn.createStatement();
    if (target != TIMESTEN) {
      stmt.execute(DROP);
      stmt.execute(DDL);
    } else {
      try {
        stmt.execute("DROP TABLE A;");
      } catch (Exception e) {}
      stmt.execute("CREATE TABLE A(id INT, data INT);");
    } 
  }

  public void ShowTable() throws SQLException {
    int numRows;
    conn.setAutoCommit(true);
    Statement stmt = conn.createStatement();

    stmt.execute(SEQSCAN);

    ResultSet rs = stmt.getResultSet();

    ResultSetMetaData rsmd = rs.getMetaData();

    if (rsmd.getColumnCount() != 2) {
      throw new SQLException("Table should have 2 columns");
    } else if (rs.next()) {
      throw new SQLException("No rows should be returned");
    }
    System.out.println("Test db created.");
  }

  /**
   * Test SeqScan and IndexScan
   *
   * @throws SQLException
   */
  public void Scan_Test() throws SQLException {
    conn.setAutoCommit(true);
    Statement stmt = conn.createStatement();
    stmt.execute(INSERT_A_1);
    stmt.execute(INSERT_A_2);
    stmt.execute(SEQSCAN);

    ResultSet rs = stmt.getResultSet();
    rs.next();
    String row1 = rs.getString(1) + "," + rs.getString(2);
    rs.next();
    String row2 = rs.getString(1) + "," + rs.getString(2);
    if (rs.next()) {
      throw new SQLException("More than 2 rows returned");
    } else if (row1.equals(row2)) {
      throw new SQLException("Rows aren't distinct");
    }

    stmt.execute(INDEXSCAN);
    stmt.execute(INDEXSCAN_COLUMN);
  }

  public void Nop_Test() throws Exception {
    long startTime = System.currentTimeMillis();
    long elapsedTime = 0L;
    long numOps = 1000 * 1000;
    if (query_type == SEMICOLON) {
      Statement stmt = conn.createStatement();
      for (long i = 0; i < numOps; i++) {
        try {
            stmt.execute(";");
        } catch (Exception e) { }
      } 
    } else {
      PreparedStatement stmt = conn.prepareStatement(INDEXSCAN_PARAM);
      conn.setAutoCommit(false);
      for (long i = 0; i < numOps; i++) {
         try {
            stmt.setInt(1, 0);
            stmt.execute();
            conn.commit();
         } catch (Exception e) { }
      }
    }
    elapsedTime = (new Date()).getTime() - startTime;
    System.out.println("Nop throughput: " + numOps * 1000 / elapsedTime);
  }


  /**
   * Insert a record
   *
   * @param n the id of the record
   * @throws SQLException
   */
  public void Insert(int n, TABLE table) throws SQLException {
    PreparedStatement stmt = null;
    switch (table) {
      case A:
        stmt = conn.prepareStatement(INSERT_A);
        break;
      case B:
        stmt = conn.prepareStatement(INSERT_B);
        break;
    }
    org.postgresql.PGStatement pgstmt = (org.postgresql.PGStatement) stmt;
    pgstmt.setPrepareThreshold(1);
    //System.out.println("Prepare threshold" + pgstmt.getPrepareThreshold());
    for (int i = 0; i < n; i++) {
      String data = table.name() + " says hello world and id = " + i;
      stmt.setInt(1, i);
      stmt.setString(2, data);
      boolean usingServerPrepare = pgstmt.isUseServerPrepare();
      int ret = stmt.executeUpdate();
      System.out.println("Used server side prepare " + usingServerPrepare + ", Inserted: " + ret);
    }
    return;
  }

  /**
   * Insert a record
   *
   * @param n the id of the record
   * @throws SQLException
   */
  public void Insert(int n) throws SQLException {
    conn.setAutoCommit(false);
    PreparedStatement stmt = conn.prepareStatement(INSERT);
    PreparedStatement stmtA = conn.prepareStatement(INSERT_A);
    PreparedStatement stmtB = conn.prepareStatement(INSERT_B);
    org.postgresql.PGStatement pgstmt = (org.postgresql.PGStatement) stmt;
    pgstmt.setPrepareThreshold(1);
    for (int i = 0; i < n; i++) {
      String data1 = "Ming says hello world and id = " + i;
      String data2 = "Joy says hello world and id = " + i;
      stmt.setInt(1, i);
      stmt.setInt(3, i);
      stmt.setString(2, data1);
      stmt.setString(4, data2);
      int ret = stmt.executeUpdate();
    }
    conn.commit();
    conn.setAutoCommit(true);
    return;
  }


  public void SeqScan() throws SQLException {
    System.out.println("\nSeqScan Test:");
    System.out.println("Query: " + SEQSCAN);
    PreparedStatement stmt = conn.prepareStatement(SEQSCAN);
    ResultSet r = stmt.executeQuery();
    while (r.next()) {
      System.out.println("SeqScan: id = " + r.getString(1) + ", " + r.getString(2));
    }
    r.close();
  }

  /**
   * Perform Index Scan with a simple equal qualifier
   *
   * @param i the param for the equal qualifier
   * @throws SQLException
   */
  public void IndexScanParam(int i) throws SQLException {
    System.out.println("\nIndexScan Test: ? = " + i);
    System.out.println("Query: " + INDEXSCAN_PARAM);
    PreparedStatement stmt = conn.prepareStatement(INDEXSCAN_PARAM);
    conn.setAutoCommit(false);
    stmt.setInt(1, i);
    stmt.execute();
    conn.commit();
  }

  public void UpdateBySeqScan() throws SQLException {
    System.out.println("\nUpdate Test: ");
    System.out.println("Query: " + UPDATE_BY_SCANSCAN);
    PreparedStatement stmt = conn.prepareStatement(UPDATE_BY_SCANSCAN);
    stmt.setString(1, "Updated");
    stmt.executeUpdate();
    System.out.println("Updated: data: Updated");

  }


  public void DeleteByIndexScan(int i) throws SQLException {
    System.out.println("\nDelete Test: ");
    System.out.println("Query: " + DELETE_BY_INDEXSCAN);
    PreparedStatement stmt = conn.prepareStatement(DELETE_BY_INDEXSCAN);
    stmt.setInt(1, i);
    stmt.executeUpdate();
    System.out.println("Deleted: id = " + i);
  }

  public void Union(int i) throws SQLException {
    PreparedStatement stmt = conn.prepareStatement(UNION);

    org.postgresql.PGStatement pgstmt = (org.postgresql.PGStatement) stmt;
    pgstmt.setPrepareThreshold(0);
    stmt.setInt(1, i);
    stmt.setInt(2, i);
    ResultSet r = stmt.executeQuery();

    System.out.println(r.getString(1));
  }


  public void Batch_Insert() throws SQLException{
    PreparedStatement stmt = conn.prepareStatement(TEMPLATE_FOR_BATCH_INSERT);

    conn.setAutoCommit(false);
    
    for(int i=1; i <= 5 ;i++){
      stmt.setInt(1,i);
      stmt.setString(2,"Yo");
      stmt.addBatch();
    }

    System.out.println("All Good Here");
    int[] res;
    try{
       res = stmt.executeBatch();
    }catch(SQLException e){
      e.printStackTrace();
      throw e.getNextException();
    }
    for(int i=0; i < res.length; i++){
      System.out.println(res[i]);
      if (res[i] < 0) {
        throw new SQLException("Query "+ (i+1) +" returned " + res[i]);
      }
    }
    conn.commit();
  }

  public void Batch_Update() throws SQLException{
    PreparedStatement stmt = conn.prepareStatement(UPDATE_BY_INDEXSCAN);

    conn.setAutoCommit(false);
    
    for(int i=1; i <= 3;i++){
      stmt.setString(1,"Cool");
      stmt.setInt(2,i);
      stmt.addBatch();
      }
      System.out.println("All Good Here");
      int[] res = stmt.executeBatch();
      for(int i=0; i < res.length; i++) {
        if (res[i] < 0) {
          throw new SQLException("Query "+ (i+1) +" returned " + res[i]);
        }
        System.out.println(res[i]);
      }
    conn.commit();
  }

  public void Batch_Delete() throws SQLException{
    PreparedStatement stmt = conn.prepareStatement(DELETE_BY_INDEXSCAN);

    conn.setAutoCommit(false);
    
    for(int i=1; i <= 3;i++){
      stmt.setInt(1,i);
      stmt.addBatch();
    }
    System.out.println("All Good Here");
    int[] res = stmt.executeBatch();
    for(int i=0; i < res.length; i++) {
      if (res[i] < 0) {
        throw new SQLException("Query "+ (i+1) +" returned " + res[i]);
      }
      System.out.println(res[i]);
    }
    conn.commit();
  }

  public void SelectParam() throws SQLException {
    PreparedStatement stmt = conn.prepareStatement(INDEXSCAN_PARAM);
    conn.setAutoCommit(false);
    stmt.setInt(1, 4);
    stmt.execute();
    conn.commit();
  }
  
  //tests inserting and reading back large binary values
  public void BlobTest() throws SQLException {
    Random rand = new Random(12345L);
    for (int i = 1; i < 20; i++) {
      Statement initstmt = conn.createStatement();
      initstmt.execute("DROP TABLE IF EXISTS A;");
      initstmt.execute("CREATE TABLE A (id INT PRIMARY KEY, data VARBINARY)");
      PreparedStatement stmt = conn.prepareStatement("INSERT INTO A VALUES (?,?);");
      
      System.out.println(i);
      int numbytes = 1 << i;
      byte[] sentbytes = new byte[numbytes];
      rand.nextBytes(sentbytes);
      stmt.setInt(1, 1);
      stmt.setBytes(2, sentbytes);
      stmt.execute();

      initstmt.execute(SEQSCAN);
      ResultSet rs = initstmt.getResultSet();
      if (!rs.next()) {
        System.err.println("failed at " + (numbytes) + " bytes");
        throw new SQLException("Did not get result after inserting into varbinary");
      }
      int recvint = rs.getInt(1);
      byte[] recvbytes = rs.getBytes(2);
      if (recvint != 1 || !Arrays.equals(sentbytes, recvbytes)) {
        System.err.println("failed at " + (numbytes) + " bytes");
        throw new SQLException(
            "byte mismatch: \n before: " + Arrays.toString(sentbytes) + "\n after:  " + Arrays.toString(recvbytes));
      }
      if (rs.next()) {
        System.err.println("failed at " + (numbytes) + " bytes");
        throw new SQLException("Too many results");
      }
    }
  }

  public static final int PELOTON = 0;
  public static final int POSTGRES = 1;
  public static final int TIMESTEN = 2;

  static public void main(String[] args) throws Exception {
      if (args.length == 0) {
        System.out.println("Please specify target: [peloton|timesten|postgres]");
        return;
      }
      int target = PELOTON;
      if (args[0].equals("peloton")) {
        target = PELOTON;
      } else if (args[0].equals("postgres")) {
        target = POSTGRES;
        // echo "Please set env var LD_LIBRARY_PATH if you're using timesten. e.g. /home/rxian/TimesTen/ttdev2/lib"
      } else if (args[0].equals("timesten")) {
        target = TIMESTEN;
      } else {
        System.out.println("Please specify target: [peloton|timesten|postgres]");
        return;
      }
      PelotonTest pt = new PelotonTest(target);
      pt.Init();
      pt.Nop_Test();
      pt.Close();
  }

}
