import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

/* -------------------------
   Color constants (ANSI)
   ------------------------- */
class Colors {
    public static final String RESET  = "\u001B[0m";
    public static final String BLACK  = "\u001B[30m";
    public static final String RED    = "\u001B[31m";
    public static final String GREEN  = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE   = "\u001B[34m";
    public static final String MAGENTA= "\u001B[35m";
    public static final String CYAN   = "\u001B[36m";
    public static final String WHITE  = "\u001B[37m";
    public static final String BOLD   = "\u001B[1m";
}

/* -------------------------
   Models: User, Train, Ticket, Passenger
   ------------------------- */
class User {
    final String username;
    private String password;
    final String mobile;
    final ArrayList<Ticket> tickets = new ArrayList<>();

    User(String username, String password, String mobile) {
        this.username = username;
        this.password = password;
        this.mobile = mobile;
    }

    boolean checkPassword(String pw) 
    { 
	return password.equals(pw); 
    }
    void setPassword(String newPw) 
    { 
	this.password = newPw;
    }
    ArrayList<Ticket> getTickets() 
    { 
	return tickets;
    }
    boolean addTicket(Ticket t)
    {
        // allow many tickets; realistic cap not enforced here
        return tickets.add(t);
    }
    Ticket findTicketByPNR(int pnr) 
    {
        for (Ticket t : tickets) 
	{
		if (t.getPnr() == pnr) 
		{
			return t;
		}
	}
        return null;
    }
    Ticket removeTicketByPNR(int pnr) {
        Iterator<Ticket> it = tickets.iterator();
        while (it.hasNext()) 
	{
            Ticket t = it.next();
            if (t.getPnr() == pnr) 
	    { 
		it.remove();
		 return t; 
	    }
        }
        return null;
    }
}

class Train {
    private final int trainNo;
    private final String trainName;
    private final String source;
    private final String destination;
    private final String depart;
    private final String arrive;
    private final double sleeperFare;
    private final double acFare;
    private final double tatkalFare;

    // seatMap: dateString "dd-MM-yyyy" -> int[] {sleeper, ac, tatkal}
    private final Map<String,int[]> seatMap = new HashMap<>();
    private final int defaultSleeper = 50;
    private final int defaultAc = 20;
    private final int defaultTatkal = 10;

    Train(int no, String name, String src, String dst, String depart, String arrive,
          double sleeperFare, double acFare, double tatkalFare) {
        this.trainNo = no;
        this.trainName = name;
        this.source = src;
        this.destination = dst;
        this.depart = depart;
        this.arrive = arrive;
        this.sleeperFare = sleeperFare;
        this.acFare = acFare;
        this.tatkalFare = tatkalFare;
    }

    int getTrainNo() 
    { 
	return trainNo; 
    }
    String getTrainName() 
    { 
	return trainName; 
    }
    String getSource() 
    { 
	return source; 
    }
    String getDestination() 
    { 
	return destination; 
    }
    String getDepart() 
    { 
	return depart; 
    }
    String getArrive() 
    { 
	return arrive; 
    }

    // ensure seat counts for a date (initialize fresh if not present)
    private int[] ensureDate(String date) {
        return seatMap.computeIfAbsent(date, d -> new int[] {defaultSleeper, defaultAc, defaultTatkal});
    }

    public int getAvailableSeats(String cls, String date) {
        int[] arr = ensureDate(date);
        switch (cls.toLowerCase()) {
            case "sleeper": return arr[0];
            case "ac": return arr[1];
            case "tatkal": return arr[2];
            default: return 0;
        }
    }

    public boolean bookSeats(String cls, int count, String date) {
        int[] arr = ensureDate(date);
        switch (cls.toLowerCase()) {
            case "sleeper":
                if (arr[0] >= count)
		{ 
			arr[0] -= count;
		 	return true; 
		}
		else 
			return false;
            case "ac":
                if (arr[1] >= count) 
		{ 
			arr[1] -= count; 
			return true; 
		} 
		else 
			return false;
            case "tatkal":
                if (arr[2] >= count) 
		{ 
			arr[2] -= count; 
			return true; 
		} 
		else 
			return false;
            default:
                return false;
        }
    }

    public void restoreSeats(String cls, int count, String date) {
        int[] arr = ensureDate(date);
        switch (cls.toLowerCase()) {
            case "sleeper": arr[0] += count; break;
            case "ac": arr[1] += count; break;
            case "tatkal": arr[2] += count; break;
        }
    }

    public double getFare(String cls) {
        switch (cls.toLowerCase()) {
            case "sleeper": return sleeperFare;
            case "ac": return acFare;
            case "tatkal": return tatkalFare;
            default: return 0.0;
        }
    }

    // print summary for date (one line)
    public void printSummaryForDate(String date) {
        int[] arr = ensureDate(date);
        System.out.printf("%d - %-20s | %s -> %s | dep:%s arr:%s | S:%d A:%d T:%d | Fare(S:₹%.0f A:₹%.0f T:₹%.0f)%n",
                trainNo, trainName, source, destination, depart, arrive,
                arr[0], arr[1], arr[2], sleeperFare, acFare, tatkalFare);
    }

    public void printSchedule() {
        System.out.printf("%d - %-20s | %s -> %s | dep:%s arr:%s | Fare S:₹%.0f A:₹%.0f T:₹%.0f%n",
                trainNo, trainName, source, destination, depart, arrive, sleeperFare, acFare, tatkalFare);
    }
}

class Passenger {
    final String name;
    final int age;
    final String gender;
    Passenger(String name, int age, String gender) {
        this.name = name;
        this.age = age;
        this.gender = gender;
    }
}

class Ticket {
    private static final AtomicInteger PNR_COUNTER = new AtomicInteger(100000);
    private final int pnr;
    private final Train train;
    private final String travelDate; // dd-MM-yyyy
    private final String travelClass;
    private final List<Passenger> passengers;
    private final double farePerPassenger;
    private final double subtotal;
    private final double gst;
    private final double total;
    private final String bookingTimestamp;

    Ticket(Train train, String travelDate, String travelClass, List<Passenger> passengers, double farePerPassenger) {
        this.pnr = PNR_COUNTER.getAndIncrement();
        this.train = train;
        this.travelDate = travelDate;
        this.travelClass = travelClass;
        this.passengers = new ArrayList<>(passengers);
        this.farePerPassenger = farePerPassenger;
        this.subtotal = round2(farePerPassenger * passengers.size());
        this.gst = round2(this.subtotal * 0.05);
        this.total = round2(this.subtotal + this.gst);
        this.bookingTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    }

    int getPnr() 
    { 
	return pnr; 
    }
    Train getTrain() 
    { 
	return train; 
    }
    String getTravelDate() 
    { 
	return travelDate; 
    }
    String getTravelClass() 
    { 
	return travelClass; 
    }
    List<Passenger> getPassengers() { return passengers; }
    double getSubtotal() { return subtotal; }
    double getGst() { return gst; }
    double getTotal() { return total; }
    String getBookingTimestamp() { return bookingTimestamp; }

    private static double round2(double v) 
   { return Math.round(v*100.0)/100.0; }
}

/* -------------------------
   Console UI helpers (printing banners, ticket box, animations)
   ------------------------- */
class ConsoleUI {
	
    static void printWelcome() {
        System.out.println(Colors.YELLOW + "==========================================" + Colors.RESET);
        System.out.println(Colors.CYAN + Colors.BOLD + "        Welcome to Railway Booking        " + Colors.RESET);
        System.out.println(Colors.YELLOW + "==========================================" + Colors.RESET);
	System.out.println();
	System.out.println();
	music();
	
	
    }

     static void music() {
        try {

		System.out.println("\n");

		System.out.println("\t\t\t\t\t AMBICA ROOPAM Song .......");
            // Correct file path with extension
            File file = new File("C:/Users/HP/Desktop/Java Project/Music/AMBICA.wav");

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();

            // keep program alive until audio finishes
            Thread.sleep(clip.getMicrosecondLength() / 1000);

        } catch (UnsupportedAudioFileException e) {
            System.out.println("Unsupported file format: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
        } catch (LineUnavailableException e) {
            System.out.println("Audio line unavailable: " + e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    static void printMenuHeader() {
        System.out.println();
        System.out.println(Colors.BLUE + "==================== MENU ====================" + Colors.RESET);
    }

    static void printMenuOptions() {
        System.out.println(Colors.GREEN + "1) Show Trains " + Colors.RESET);
        System.out.println(Colors.GREEN + "2) Book Ticket(s)" + Colors.RESET);
        System.out.println(Colors.GREEN + "3) View My Tickets" + Colors.RESET);
        System.out.println(Colors.GREEN + "4) Cancel Ticket" + Colors.RESET);
        System.out.println(Colors.GREEN + "5) Check PNR" + Colors.RESET);
        System.out.println(Colors.GREEN + "6) Check Seats " + Colors.RESET);
        System.out.println(Colors.GREEN + "7) Show Train Schedule" + Colors.RESET);
        System.out.println(Colors.GREEN + "8) Change Password " + Colors.RESET);
        System.out.println(Colors.GREEN + "9) Profile" + Colors.RESET);
        System.out.println(Colors.RED   + "10) Logout" + Colors.RESET);
        System.out.println(Colors.BLUE + "===============================================" + Colors.RESET);
    }

    static void loading(String msg, int secs) {
        System.out.print(msg);
        for (int i = 0; i < secs; i++) {
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.print(".............");
        }
        System.out.println();
    }

    static void typeWrite(String msg, int delayMs) {
        for (char c : msg.toCharArray()) {
            System.out.print(c);
            try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        System.out.println();
    }

    // Print a clean Unicode ticket box width 66 characters

    static void printTicketBox(Ticket t) {
        String top = Colors.YELLOW + "┌" + "─".repeat(66) + "┐" + Colors.RESET;
        String midSep = Colors.YELLOW + "├" + "─".repeat(66) + "┤" + Colors.RESET;
        String bot = Colors.YELLOW + "└" + "─".repeat(66) + "┘" + Colors.RESET;

        System.out.println(top);
        String title = "IRCTC - TICKET";
        System.out.printf(Colors.CYAN + "│%s%"+(66 - title.length())+"s│%n" + Colors.RESET, centerPad(title, 66), "");
        System.out.println(midSep);

        System.out.printf(Colors.WHITE + "│ PNR: %-14d  Train: %-30s │%n" + Colors.RESET, t.getPnr(), t.getTrain().getTrainName() + " (" + t.getTrain().getTrainNo() + ")");
        System.out.printf(Colors.WHITE + "│ Route: %-28s  Date: %-15s │%n" + Colors.RESET, t.getTrain().getSource() + " -> " + t.getTrain().getDestination(), t.getTravelDate());
        System.out.printf(Colors.WHITE + "│ Class: %-10s  Passengers: %-38s │%n" + Colors.RESET, t.getTravelClass(), "");
        for (Passenger p : t.getPassengers()) {
            String pinfo = String.format("%s (Age:%d,%s)", p.name, p.age, p.gender);
            System.out.printf(Colors.WHITE + "│ - %-62s │%n" + Colors.RESET, pinfo);
        }

        System.out.println(midSep);
        System.out.printf(Colors.WHITE + "│ Fare (subtotal): ₹%-12.2f  GST(5%%): ₹%-10.2f  Total: ₹%-10.2f %n" + Colors.RESET, t.getSubtotal(), t.getGst(), t.getTotal());
        System.out.printf(Colors.WHITE + "│ Booked On: %-52s %n" + Colors.RESET, t.getBookingTimestamp());
        System.out.println(midSep);
        System.out.printf(Colors.CYAN + "│%s%"+(66 - 24)+"s %n" + Colors.RESET, centerPad("Thank you for booking with IRCTC", 66), "");
        System.out.println(bot);
	//neenane();

    }

// neenane music
	/*static void neenane()
	{
		try {
            // Correct file path with extension


		System.out.print("\n\n\nChiitttiiiii.....  ----- oooovvuu ovvu oooovuuuoovv ----- neeenanenana nenananeee ----- Dehradun bey Dehradun!!!!!\n\n\n");

            File file2 = new File("C:/Users/HP/Desktop/Java Project/Music/neenane.wav");

            AudioInputStream audioStream2 = AudioSystem.getAudioInputStream(file2);
            Clip clip2 = AudioSystem.getClip();
            clip2.open(audioStream2);
            clip2.start();
		

            // keep program alive until audio finishes
            Thread.sleep(clip2.getMicrosecondLength() / 1000);

        	}
		catch (UnsupportedAudioFileException e)
		 {
            		System.out.println("Unsupported file format: " + e.getMessage());
        	} catch (IOException e) {
            	System.out.println("File error: " + e.getMessage());
        	} catch (LineUnavailableException e) {
            	System.out.println("Audio line unavailable: " + e.getMessage());
        	} 
		catch (InterruptedException e) {
        	    e.printStackTrace();
        	}

	}*/

    static String centerPad(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        int left = (width - s.length())/2;
        int right = width - s.length() - left;
        return " ".repeat(left) + s + " ".repeat(right);
    }
}

/* -------------------------
   Main application class
   ------------------------- */
public class IRCTCApp {
    private static final Scanner sc = new Scanner(System.in);
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Map<String, User> users = new HashMap<>();
    private static final List<Train> trains = new ArrayList<>();
    private static User currentUser = null;

    public static void main(String[] args) {
        // preload demo users (2 users)
        users.put("divakar", new User("divakar", "Divakar@123", "8341905135"));
        users.put("admin", new User("admin", "Admin@123", "9876500002"));

        // preload 20 trains silently
        preloadTrains();

        // welcome
        ConsoleUI.printWelcome();

        // main login/register loop
        while (true) {
            System.out.println();
            System.out.println(Colors.GREEN + "1) Login" + Colors.RESET);
            System.out.println(Colors.GREEN + "2) Register" + Colors.RESET);
            System.out.println(Colors.RED   + "3) Exit" + Colors.RESET);
            System.out.print("Choice: ");
            String choice = sc.nextLine().trim();
            if ("1".equals(choice)) {
		//System.out.print(Colors.GREEN+"Enter 1 for login and 2 for back "+Colors.RESET);
		//int n = sc.nextInt();
	                	loginFlow();
               	 	if (currentUser != null) { // proceed to menu
                    		menuLoop();
                	}
	            } else if ("2".equals(choice)) {
                registerFlow();
            } else if ("3".equals(choice)) {
                //System.out.println(Colors.YELLOW + "Thankyou Malli randi..BYe..BYe.." + Colors.RESET);
                break;
            } else {
                System.out.println(Colors.RED + "Invalid choice. Koncham Choice Chusi ivvu Saami..." + Colors.RESET);
            }
        }
    }

    /* -------------------------
       Preload 20 trains (silent)
       ------------------------- */
    private static void preloadTrains() {
        trains.add(new Train(12627, "Karnataka Express", "Bengaluru", "Delhi", "06:00", "06:00", 600, 1500, 1800));
        trains.add(new Train(12628, "Karnataka Express (Return)", "Delhi", "Bengaluru", "18:00", "18:00", 600, 1500, 1800));
        trains.add(new Train(10101, "Hyderabad Express", "Hyderabad", "Chennai", "07:00", "12:30", 500, 1200, 1500));
        trains.add(new Train(10102, "Mumbai Express", "Mumbai", "Delhi", "08:00", "20:00", 600, 1300, 1600));
        trains.add(new Train(10103, "Bangalore Express", "Bangalore", "Kolkata", "09:00", "23:30", 550, 1250, 1550));
        trains.add(new Train(10104, "Chennai Express", "Chennai", "Mumbai", "06:30", "16:30", 500, 1200, 1500));
        trains.add(new Train(10105, "Delhi Mail", "Delhi", "Kolkata", "05:00", "22:00", 650, 1350, 1650));
        trains.add(new Train(10106, "Kolkata Mail", "Kolkata", "Bangalore", "10:00", "02:00", 600, 1300, 1600));
        trains.add(new Train(10107, "Lucknow Express", "Lucknow", "Delhi", "07:30", "13:00", 500, 1200, 1500));
        trains.add(new Train(10108, "Patna Express", "Patna", "Mumbai", "11:00", "03:00", 550, 1250, 1550));
        trains.add(new Train(10109, "Ahmedabad Express", "Ahmedabad", "Chennai", "05:30", "20:00", 500, 1200, 1500));
        trains.add(new Train(10110, "Jaipur Express", "Jaipur", "Delhi", "06:00", "12:00", 600, 1300, 1600));
        trains.add(new Train(10111, "Bhopal Express", "Bhopal", "Mumbai", "09:00", "19:00", 550, 1250, 1550));
        trains.add(new Train(10112, "Nagpur Express", "Nagpur", "Chennai", "08:00", "22:00", 500, 1200, 1500));
        trains.add(new Train(10113, "Indore Express", "Indore", "Delhi", "07:00", "17:00", 650, 1350, 1650));
        trains.add(new Train(10114, "Pune Express", "Pune", "Bangalore", "06:00", "14:00", 600, 1300, 1600));
        trains.add(new Train(10115, "Goa Express", "Goa", "Mumbai", "10:00", "15:00", 500, 1200, 1500));
        trains.add(new Train(10116, "Surat Express", "Surat", "Delhi", "05:00", "18:00", 550, 1250, 1550));
        trains.add(new Train(10117, "Mysore Express", "Mysore", "Bangalore", "11:00", "13:00", 500, 1200, 1500));
        trains.add(new Train(10118, "Coimbatore Express", "Coimbatore", "Chennai", "07:30", "11:30", 600, 1300, 1600));
        // silent load - no console print
    }

    /* -------------------------
       Registration flow
       ------------------------- */
    private static void registerFlow() {
    System.out.println();
    System.out.println(Colors.CYAN + "------ REGISTER ------" + Colors.RESET);
    System.out.print("Enter username (min 4 chars): ");
    String uname = sc.nextLine().trim();
    if (uname.length() < 4) {
        System.out.println(Colors.RED + "Username too short." + Colors.RESET);
        return;
    }
    if (users.containsKey(uname)) {
        System.out.println(Colors.RED + "Username already exists." + Colors.RESET);
        return;
    }

    // Loop for mobile number
    String mob;
    while (true) {
        System.out.print("Enter mobile (10 digits): ");
        mob = sc.nextLine().trim();
        if (mob.matches("\\d{10}")) {
            break; // valid mobile
        } else {
            System.out.println(Colors.RED + "Invalid mobile number.Okka Mobile Number kuda sarigga ivvaleva guruji..." + Colors.RESET);
            System.out.print(Colors.BLUE + "1. Re-enter Mobile  2. Back to Menu: " + Colors.RESET);
            String choice = sc.nextLine().trim();
            if (!choice.equals("1")) {
                System.out.println("Returning to main menu...");
                return;
            }
        }
    }

    // Loop for password
    System.out.println("Password rules: min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special (@#$%&*!)");
    String pw;
    while (true) {
        System.out.print("Enter password: ");
        pw = sc.nextLine();
        if (isStrongPassword(pw)) {
            break; // valid password
        } else {
            System.out.println(Colors.RED + "Password does not meet requirements.Password Rules taggattu password ivvu ayya.... " + Colors.RESET);
            System.out.print(Colors.BLUE + "1. Re-enter Password  2. Back to Menu: " + Colors.RESET);
            String choice = sc.nextLine().trim();
            if (!choice.equals("1")) {
                System.out.println("Returning to main menu...");
                return;
            }
        }
    }

    users.put(uname, new User(uname, pw, mob));
    System.out.println(Colors.GREEN + "Registration successful.Velli login ayyipo okka nimisham kuda wait cheyaku po...  " + Colors.RESET);
}


    /* -------------------------
       Login flow
       ------------------------- */


	/*public static void sairam()
	{
			try {
            // Correct file path with extension

		System.out.println("\n\n\n \t\t Saiii Rammm \t\t Saiii Rammm \t\t Saiii Rammm  \t\t  Saiii Rammm  \t\t Saiii Rammm \n\n\n");

            File file2 = new File("C:/Users/HP/Desktop/Java Project/Music/unqgamer.wav");

            AudioInputStream audioStream2 = AudioSystem.getAudioInputStream(file2);
            Clip clip2 = AudioSystem.getClip();
            clip2.open(audioStream2);
            clip2.start();
		

            // keep program alive until audio finishes
            Thread.sleep(clip2.getMicrosecondLength() / 1000);

        	}
		catch (UnsupportedAudioFileException e)
		 {
            		System.out.println("Unsupported file format: " + e.getMessage());
        	} catch (IOException e) {
            	System.out.println("File error: " + e.getMessage());
        	} catch (LineUnavailableException e) {
            	System.out.println("Audio line unavailable: " + e.getMessage());
        	} 
		catch (InterruptedException e) {
        	    e.printStackTrace();
        	}

	}*/

	/*public static void Randi()
	{

	try {
            // Correct file path with extension
		System.out.println("\n\n\n Haa Randi Randii...Kurchondi!!!!  --- Eeelopuu Haa Sir ante --- Aaaaa  Tea Ivvandiiiiiii\n\n\n");
            File file = new File("C:/Users/HP/Desktop/Java Project/Music/Randi.wav");

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
		

            // keep program alive until audio finishes
            Thread.sleep(clip.getMicrosecondLength() / 1000);

        	}

	 	catch (UnsupportedAudioFileException e)
		 {
            		System.out.println("Unsupported file format: " + e.getMessage());
        	} catch (IOException e) {
            	System.out.println("File error: " + e.getMessage());
        	} catch (LineUnavailableException e) {
            	System.out.println("Audio line unavailable: " + e.getMessage());
        	} catch (InterruptedException e) {
        	    e.printStackTrace();
        	}
	}*/

   private static void loginFlow() {
    System.out.println();
    System.out.println(Colors.CYAN + "------ LOGIN ------" + Colors.RESET);
    System.out.print("Username: ");
    String uname = sc.nextLine().trim();
    if (!users.containsKey(uname)) {
        System.out.println(Colors.RED + "User not found. Register avvakunda yela nanna login ayyipovadaniki vachesaav..." + Colors.RESET);
        return;
    }

    User u = users.get(uname);
    while (true) {
        System.out.print("Password: ");
        String pw = sc.nextLine();
        if (u.checkPassword(pw)) {
            currentUser = u;
            ConsoleUI.typeWrite("Login successful", 100);		
            ConsoleUI.loading("", 1);
		//Randi();

            break; // exit loop on success
        } 
	else {
            System.out.println(Colors.RED + "Incorrect password." + Colors.RESET);
		//sairam();

            System.out.print("Try again? (yes/no): ");
            String choice = sc.nextLine().trim().toLowerCase();
            if (!choice.equals("yes") && !choice.equals("y")) {
                System.out.println("Returning to main menu...");
                break; // stop login attempt
            }
        }
    }
}


    /* -------------------------
       Menu loop (10 options)
       ------------------------- */
    private static void menuLoop() {
        while (true) {
            ConsoleUI.printMenuHeader();
            ConsoleUI.printMenuOptions();
            System.out.print("Choice: ");
            String ch = sc.nextLine().trim();
            switch (ch) {
                case "1": showTrainsByDate(); break;
                case "2": bookTicketsFlow(); break;
                case "3": viewMyTickets(); break;
                case "4": cancelTicketFlow(); break;
                case "5": checkPNRFlow(); break;
                case "6": checkSeatsFlow(); break;
                case "7": showTrainSchedule(); break;
                case "8": changePasswordOTP(); break;
                case "9": profile(); break;
                case "10": System.out.println(Colors.YELLOW + "Logging out..." + Colors.RESET); currentUser = null; return;
                default: System.out.println(Colors.RED + "Invalid choice (1..10)." + Colors.RESET);
            }
        }
    }

    /* -------------------------
       1) Show Trains (date)
       - If no availability created for date, initialize (fresh) and show seats.
       - If all trains have zero seats for that date, message "No trains available for this date."
       ------------------------- */
    private static void showTrainsByDate() {
    if (currentUser == null) {
        System.out.println(Colors.RED + "Please login first." + Colors.RESET);
        return;
    }

    String dateStr;
    while (true) {
        System.out.print("Enter travel date (dd-MM-yyyy) [press Enter for today]: ");
        String dateInput = sc.nextLine().trim();

        if (dateInput.isEmpty()) {
            dateStr = LocalDate.now().format(dateFormatter);
            break;
        }

        try {
            LocalDate parsedDate = LocalDate.parse(dateInput, dateFormatter);

            if (parsedDate.isBefore(LocalDate.now())) {
                System.out.println(Colors.RED + "Past dates are not allowed." + Colors.RESET);
                System.out.print(Colors.BLUE + "1. Re-enter Date  2. Back to Menu: " + Colors.RESET);
                String choice = sc.nextLine().trim();
                if (!choice.equals("1")) {
                    System.out.println("Returning to main menu...");
                    return;
                }
            } else {
                dateStr = parsedDate.format(dateFormatter);
                break;
            }

        } catch (DateTimeParseException ex) {
            System.out.println(Colors.RED + "Invalid date format." + Colors.RESET);
            System.out.print(Colors.BLUE + "1. Re-enter Date  2. Back to Menu: " + Colors.RESET);
            String choice = sc.nextLine().trim();
            if (!choice.equals("1")) {
                System.out.println("Returning to main menu...");
                return;
            }
        }
    }

    boolean any = false;
    System.out.println(Colors.BLUE + "Available trains for " + dateStr + ":" + Colors.RESET);
    for (Train t : trains) {
        int s = t.getAvailableSeats("sleeper", dateStr);
        int a = t.getAvailableSeats("ac", dateStr);
        int tt = t.getAvailableSeats("tatkal", dateStr);
        if (s > 0 || a > 0 || tt > 0) {
            any = true;
            t.printSummaryForDate(dateStr);
        }
    }
    if (!any) System.out.println(Colors.RED + "No trains available for this date." + Colors.RESET);
}


    /* -------------------------
       2) Book Ticket(s)
       - choose source & destination to find trains (show multiple)
       - choose train, class, add multiple passengers
       - after each passenger: add another / proceed to payment / cancel
       - payment (UPI or Card) with cancel option
       ------------------------- */
    private static void bookTicketsFlow() {
    if (currentUser == null) {
        System.out.println(Colors.RED + "Please login first." + Colors.RESET);
        return;
    }

    System.out.print("From (source): ");
    String src = sc.nextLine().trim();
    System.out.print("To (destination): ");
    String dst = sc.nextLine().trim();

    String date;
    while (true) {
        System.out.print("Travel date (dd-MM-yyyy): ");
        date = sc.nextLine().trim();

        try {
            LocalDate parsedDate = LocalDate.parse(date, dateFormatter);
            LocalDate today = LocalDate.now();
            LocalDate maxAllowed = today.plusMonths(2);

            if (parsedDate.isBefore(today)) {
                System.out.println(Colors.RED + "Past dates are not allowed." + Colors.RESET);
                System.out.print(Colors.BLUE + "1. Re-enter Date  2. Back to Menu: " + Colors.RESET);
                String choice = sc.nextLine().trim();
                if (!choice.equals("1")) {
                    System.out.println("Returning to main menu...");
                    return;
                }
            } else if (parsedDate.isAfter(maxAllowed)) {
                System.out.println(Colors.RED + "You can only book tickets up to 2 months ahead." + Colors.RESET);
                System.out.print(Colors.BLUE + "1. Re-enter Date  2. Back to Menu: " + Colors.RESET);
                String choice = sc.nextLine().trim();
                if (!choice.equals("1")) {
                    System.out.println("Returning to main menu...");
                    return;
                }
            } else {
                // valid date
                date = parsedDate.format(dateFormatter);
                break;
            }

        } catch (DateTimeParseException ex) {
            System.out.println(Colors.RED + "Invalid date format." + Colors.RESET);
            System.out.print(Colors.BLUE + "1. Re-enter Date  2. Back to Menu: " + Colors.RESET);
            String choice = sc.nextLine().trim();
            if (!choice.equals("1")) {
                System.out.println("Returning to main menu...");
                return;
            }
        }
    }

    // find trains matching route
    List<Train> options = new ArrayList<>();
    for (Train t : trains) {
        if (t.getSource().equalsIgnoreCase(src) && t.getDestination().equalsIgnoreCase(dst)) {
            t.getAvailableSeats("sleeper", date);
            t.getAvailableSeats("ac", date);
            t.getAvailableSeats("tatkal", date);
            options.add(t);
        }
    }
    if (options.isEmpty()) {
        System.out.println(Colors.RED + "No trains for that route." + Colors.RESET);
        return;
    }

    System.out.println(Colors.BLUE + "Available trains:" + Colors.RESET);
    for (Train t : options) {
        System.out.printf("%d: %s | dep:%s arr:%s | Fare S:₹%.0f A:₹%.0f T:₹%.0f | Seats S:%d A:%d T:%d%n",
                t.getTrainNo(), t.getTrainName(), t.getDepart(), t.getArrive(),
                t.getFare("sleeper"), t.getFare("ac"), t.getFare("tatkal"),
                t.getAvailableSeats("sleeper", date),
                t.getAvailableSeats("ac", date),
                t.getAvailableSeats("tatkal", date));
    }

    System.out.print("Enter train number to book: ");
    String trainNoStr = sc.nextLine().trim();
    int trainNo;
    try {
        trainNo = Integer.parseInt(trainNoStr);
    } catch (NumberFormatException ex) {
        System.out.println(Colors.RED + "Invalid train number." + Colors.RESET);
        return;
    }

    Train chosen = null;
    for (Train t : options) if (t.getTrainNo() == trainNo) { chosen = t; break; }
    if (chosen == null) {
        System.out.println(Colors.RED + "Train not among options." + Colors.RESET);
        return;
    }

    System.out.print("Enter class (Sleeper/AC/Tatkal): ");
    String cls = sc.nextLine().trim();
    if (!(cls.equalsIgnoreCase("Sleeper") || cls.equalsIgnoreCase("AC") || cls.equalsIgnoreCase("Tatkal"))) {
        System.out.println(Colors.RED + "Invalid class." + Colors.RESET);
        return;
    }

    // ensure enough seats: we'll gather passengers then check
    List<Passenger> passengerList = new ArrayList<>();
    while (true) {
        System.out.print("Passenger name: ");
        String pname = sc.nextLine().trim();
        if (pname.isEmpty()) {
            System.out.println(Colors.RED + "Name cannot be empty." + Colors.RESET);
            continue;
        }

        int age = -1;
        while (age < 0) {
            System.out.print("Age: ");
            String ageStr = sc.nextLine().trim();
            if (ageStr.matches("\\d+")) {
                age = Integer.parseInt(ageStr);
            } else {
                System.out.println(Colors.RED + "Age must be digits only." + Colors.RESET);
            }
        }

        String gender = "";
        while (true) {
            System.out.print("Gender (Male/Female): ");
            gender = sc.nextLine().trim();
            if (gender.equalsIgnoreCase("Male") || gender.equalsIgnoreCase("Female")) break;
            System.out.println(Colors.RED + "Gender must be Male or Female." + Colors.RESET);
        }

        passengerList.add(new Passenger(pname, age, capitalize(gender)));

        // ask next action
        System.out.print("Add another passenger (A) / Proceed to Payment (P) / Cancel (C): ");
        String opt = sc.nextLine().trim();
        if (opt.equalsIgnoreCase("A")) continue;
        if (opt.equalsIgnoreCase("P")) break;
        if (opt.equalsIgnoreCase("C")) {
            System.out.println(Colors.YELLOW + "Booking cancelled by user." + Colors.RESET);
            return;
        }
        break; // default -> proceed
    }

    int available = chosen.getAvailableSeats(cls, date);
    if (available < passengerList.size()) {
        System.out.println(Colors.RED + "Not enough seats. Available: " + available + Colors.RESET);
        return;
    }

    double perSeat = chosen.getFare(cls);
    double subtotal = perSeat * passengerList.size();
    double gst = Math.round(subtotal * 0.05 * 100.0) / 100.0;
    double total = Math.round((subtotal + gst) * 100.0) / 100.0;

    System.out.println(Colors.BLUE + "Fare Summary:" + Colors.RESET);
    System.out.printf("Per seat: ₹%.2f  Seats: %d  Subtotal: ₹%.2f  GST(5%%): ₹%.2f  Total: ₹%.2f%n",
            perSeat, passengerList.size(), subtotal, gst, total);

    boolean paid = paymentFlow(total);
    if (!paid) {
        System.out.println(Colors.RED + "Payment not completed. Booking aborted." + Colors.RESET);
        return;
    }
	
    boolean booked = chosen.bookSeats(cls, passengerList.size(), date);
    if (!booked) {
        System.out.println(Colors.RED + "Failed to reserve seats (race condition). Payment will be refunded (simulated)." + Colors.RESET);
        return;
    }

    Ticket ticket = new Ticket(chosen, date, cls, passengerList, perSeat);
    currentUser.addTicket(ticket);

    System.out.println(Colors.GREEN + "Booking confirmed! PNR: " + ticket.getPnr() + Colors.RESET);
    ConsoleUI.printTicketBox(ticket);
	
}
    /* -------------------------
       Payment flow
       - UPI pattern: local@bank or name@upi (simple validation)
       - Card: 16-digit + 3-digit CVV + expiry mm/yy (basic check)
       - Option to cancel payment during flow
       ------------------------- */
    private static boolean paymentFlow(double amount) {
        System.out.println();
        System.out.println(Colors.CYAN + "---- PAYMENT ----" + Colors.RESET);
        System.out.println("Amount to pay: ₹" + amount);
        System.out.println("1) UPI");
        System.out.println("2) Card (Credit/Debit)");
        System.out.println("3) Cancel Payment");
        System.out.print("Choose: ");
        String ch = sc.nextLine().trim();
        if ("3".equals(ch)) {
            System.out.println(Colors.YELLOW + "Payment cancelled." + Colors.RESET);
            return false;
        }
        if ("1".equals(ch)) {
            System.out.print("Enter UPI ID (example: name@bank): ");
            String upi = sc.nextLine().trim();
            if (!upi.matches("[a-zA-Z0-9._%+-]{3,}@\\w{2,}")) {
                System.out.println(Colors.RED + "Invalid UPI ID." + Colors.RESET);
                return false;
            }
            // ask confirm
            System.out.print("Confirm payment? (Y to pay / C to cancel): ");
            String conf = sc.nextLine().trim();
            if (conf.equalsIgnoreCase("C")) { System.out.println(Colors.YELLOW + "Payment cancelled." + Colors.RESET); return false; }
            ConsoleUI.loading("Processing UPI", 2);
            System.out.println(Colors.GREEN + "Payment successful via UPI." + Colors.RESET);		
            return true;
        } else if ("2".equals(ch)) {
            System.out.print("Enter 16-digit card number: ");
            String card = sc.nextLine().trim();
            if (!card.matches("\\d{16}")) { System.out.println(Colors.RED + "Invalid card number." + Colors.RESET); return false; }
            System.out.print("Enter expiry (MM/YY): ");
            String exp = sc.nextLine().trim();
            if (!exp.matches("(0[1-9]|1[0-2])/\\d{2}")) { System.out.println(Colors.RED + "Invalid expiry." + Colors.RESET); return false; }
            System.out.print("Enter CVV (3 digits): ");
            String cvv = sc.nextLine().trim();
            if (!cvv.matches("\\d{3}")) { System.out.println(Colors.RED + "Invalid CVV." + Colors.RESET); return false; }
            // confirm or cancel
            System.out.print("Confirm payment? (Y to pay / C to cancel): ");
            String conf = sc.nextLine().trim();
            if (conf.equalsIgnoreCase("C")) { System.out.println(Colors.YELLOW + "Payment cancelled." + Colors.RESET); return false; }
            ConsoleUI.loading("Processing card payment", 2);
            System.out.println(Colors.GREEN + "Card payment successful." + Colors.RESET);
            return true;
        } else {
            System.out.println(Colors.RED + "Invalid payment option." + Colors.RESET);
            return false;
        }
    }

    /* -------------------------
       3) View My Tickets
       ------------------------- */
    private static void viewMyTickets() {
        if (currentUser == null) { System.out.println(Colors.RED + "Please login." + Colors.RESET); return; }
        ArrayList<Ticket> list = currentUser.getTickets();
        if (list.isEmpty()) {
            System.out.println(Colors.YELLOW + "No tickets booked." + Colors.RESET);
            return;
        }
        for (Ticket t : list) {
            ConsoleUI.printTicketBox(t);
        }
    }

    /* -------------------------
       4) Cancel Ticket
       - ask PNR, remove ticket from user, restore seats
       ------------------------- */
    private static void cancelTicketFlow() {
        if (currentUser == null) { System.out.println(Colors.RED + "Please login." + Colors.RESET); return; }
        System.out.print("Enter PNR to cancel: ");
        String p = sc.nextLine().trim();
        int pnr;
        try { pnr = Integer.parseInt(p); } catch (NumberFormatException ex) { System.out.println(Colors.RED + "Invalid PNR." + Colors.RESET); return; }

        Ticket t = currentUser.findTicketByPNR(pnr);
        if (t == null) {
            System.out.println(Colors.RED + "PNR not found in your tickets." + Colors.RESET);
            return;
        }

        // confirm
        ConsoleUI.printTicketBox(t);
        System.out.print("Confirm cancellation? (Y/N): ");
        String conf = sc.nextLine().trim();
        if (!conf.equalsIgnoreCase("Y")) { System.out.println(Colors.YELLOW + "Cancellation aborted." + Colors.RESET); return; }

        // restore seats
        Train tr = t.getTrain();
        tr.restoreSeats(t.getTravelClass(), t.getPassengers().size(), t.getTravelDate());
        currentUser.removeTicketByPNR(pnr);
        ConsoleUI.loading("Processing cancellation", 2);
        System.out.println(Colors.GREEN + "Ticket cancelled and seats restored." + Colors.RESET);
    }

    /* -------------------------
       5) Check PNR
       ------------------------- */
    private static void checkPNRFlow() {
        if (currentUser == null) { System.out.println(Colors.RED + "Please login." + Colors.RESET); return; }
        System.out.print("Enter PNR: ");
        String s = sc.nextLine().trim();
        int pnr;
        try { pnr = Integer.parseInt(s); } catch (NumberFormatException ex) { System.out.println(Colors.RED + "Invalid PNR." + Colors.RESET); return; }
        Ticket t = currentUser.findTicketByPNR(pnr);
        if (t == null) {
            System.out.println(Colors.RED + "PNR not found." + Colors.RESET);
            return;
        }
        ConsoleUI.printTicketBox(t);
    }

    /* -------------------------
       6) Check Seats (route + date)
       ------------------------- */
    private static void checkSeatsFlow() {
        System.out.print("From: ");
        String src = sc.nextLine().trim();
        System.out.print("To: ");
        String dst = sc.nextLine().trim();
        System.out.print("Date (dd-MM-yyyy): ");
        String date = sc.nextLine().trim();
        try { LocalDate.parse(date, dateFormatter); } catch (DateTimeParseException ex) { System.out.println(Colors.RED + "Invalid date." + Colors.RESET); return; }

        boolean found = false;
        for (Train t : trains) {
            if (t.getSource().equalsIgnoreCase(src) && t.getDestination().equalsIgnoreCase(dst)) {
                found = true;
                System.out.printf("%d - %s | Seats S:%d A:%d T:%d | Fare S:₹%.0f A:₹%.0f T:₹%.0f%n",
                        t.getTrainNo(), t.getTrainName(),
                        t.getAvailableSeats("sleeper", date), t.getAvailableSeats("ac", date), t.getAvailableSeats("tatkal", date),
                        t.getFare("sleeper"), t.getFare("ac"), t.getFare("tatkal"));
            }
        }
        if (!found) System.out.println(Colors.RED + "No trains for this route." + Colors.RESET);
    }

    /* -------------------------
       7) Show Train Schedule
       ------------------------- */
    private static void showTrainSchedule() {
        System.out.print("Enter train number (or press Enter to list all): ");
        String s = sc.nextLine().trim();
        if (s.isEmpty()) {
            System.out.println(Colors.BLUE + "All train schedules:" + Colors.RESET);
            for (Train t : trains) t.printSchedule();
            return;
        }
        try {
            int num = Integer.parseInt(s);
            for (Train t : trains) {
                if (t.getTrainNo() == num) {
                    t.printSchedule();
                    return;
                }
            }
            System.out.println(Colors.RED + "Train not found." + Colors.RESET);
        } catch (NumberFormatException ex) {
            System.out.println(Colors.RED + "Invalid train number." + Colors.RESET);
        }
    }

    /* -------------------------
       8) Change Password via OTP
       - generates random 6-digit OTP, shows (demo), shows countdown, user enters
       - enforces strong password rules
       ------------------------- */
    private static void changePasswordOTP() {
        if (currentUser == null) { System.out.println(Colors.RED + "Please login." + Colors.RESET); return; }
        System.out.println(Colors.CYAN + "Change Password - Mobile verification" + Colors.RESET);
        System.out.print("Enter registered mobile number: ");
        String mob = sc.nextLine().trim();
        if (!mob.equals(currentUser.mobile)) { System.out.println(Colors.RED + "Mobile mismatch." + Colors.RESET); return; }

        Random rnd = new Random();
        int otp = 100000 + rnd.nextInt(900000);
        System.out.println(Colors.YELLOW + "OTP (demo): " + otp + " (valid for 30 seconds)" + Colors.RESET);

        // Give user time to read OTP and then ask to enter
        // We show a countdown for 30 seconds (user should enter before expiry)
        long start = System.currentTimeMillis();
        final int timeoutSec = 30;
        // Prompt user to enter OTP; we will check elapsed time after entry
        System.out.print("Enter OTP: ");
        String entered = sc.nextLine().trim();
        long elapsed = (System.currentTimeMillis() - start) / 1000L;
        if (elapsed > timeoutSec) {
            System.out.println(Colors.RED + "OTP expired (" + elapsed + "s). Please retry." + Colors.RESET);
            return;
        }
        try {
            int ent = Integer.parseInt(entered);
            if (ent != otp) { System.out.println(Colors.RED + "Incorrect OTP." + Colors.RESET); return; }
        } catch (NumberFormatException ex) { System.out.println(Colors.RED + "Invalid OTP input." + Colors.RESET); return; }

        // OTP verified - ask new password
        System.out.println("Enter new password (rules: min 8, 1 uppercase, 1 lowercase, 1 digit, 1 special):");
        String npw = sc.nextLine();
        if (!isStrongPassword(npw)) {
            System.out.println(Colors.RED + "Password does not meet requirements." + Colors.RESET);
            return;
        }
        currentUser.setPassword(npw);
        System.out.println(Colors.GREEN + "Password changed successfully." + Colors.RESET);
    }

    /* -------------------------
       9) Profile
       ------------------------- */
    private static void profile() {
        if (currentUser == null) { System.out.println(Colors.RED + "Please login." + Colors.RESET); return; }
        System.out.println(Colors.CYAN + "------ PROFILE ------" + Colors.RESET);
        System.out.println("Username: " + currentUser.username);
        System.out.println("Mobile  : " + currentUser.mobile);
        System.out.println("Tickets : " + currentUser.getTickets().size());
    }

    /* -------------------------
       Helpers
       ------------------------- */
    private static boolean isStrongPassword(String pw) {
    if (pw == null) return false;
    if (pw.length() < 8) return false;

    boolean hasUpper = false;
    boolean hasLower = false;
    boolean hasDigit = false;
    boolean hasSpecial = false;
    String specials = "@#$%&*!";

    for (char c : pw.toCharArray()) {
        if (Character.isUpperCase(c)) hasUpper = true;
        else if (Character.isLowerCase(c)) hasLower = true;
        else if (Character.isDigit(c)) hasDigit = true;
        else if (specials.indexOf(c) >= 0) hasSpecial = true;
    }
    return hasUpper && hasLower && hasDigit && hasSpecial;
}

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
    }
}