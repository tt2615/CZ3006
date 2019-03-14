/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/
import java.util.Timer;
import java.util.TimerTask;

public class SWP {

/*========================================================================*
 the following are provided, do not change them!!
 *========================================================================*/
   //the following are protocol constants.
   public static final int MAX_SEQ = 7; 
   public static final int NR_BUFS = (MAX_SEQ + 1)/2;

   // the following are protocol variables
   private int oldest_frame = 0;
   private PEvent event = new PEvent();  
   private Packet out_buf[] = new Packet[NR_BUFS];

   //the following are used for simulation purpose only
   private SWE swe = null;
   private String sid = null;  

   //Constructor
   public SWP(SWE sw, String s){
      swe = sw;
      sid = s;
   }

   //the following methods are all protocol related
   private void init(){
      for (int i = 0; i < NR_BUFS; i++){
	     out_buf[i] = new Packet();
      }
   }

   private void wait_for_event(PEvent e){
      swe.wait_for_event(e); //may be blocked
      oldest_frame = e.seq;  //set timeout frame seq
   }

   private void enable_network_layer(int nr_of_bufs) {
   //network layer is permitted to send if credit is available
	   swe.grant_credit(nr_of_bufs);
   }

   private void from_network_layer(Packet p) {
      swe.from_network_layer(p);
   }

   private void to_network_layer(Packet packet) {
	     swe.to_network_layer(packet);
   }

   private void to_physical_layer(PFrame fm)  {
      System.out.println("SWP: Sending frame: seq = " + fm.seq + 
			    " ack = " + fm.ack + " kind = " + 
			    PFrame.KIND[fm.kind] + " info = " + fm.info.data );
      System.out.flush();
      swe.to_physical_layer(fm);
   }

   private void from_physical_layer(PFrame fm) {
      PFrame fm1 = swe.from_physical_layer(); 
	   fm.kind = fm1.kind;
	   fm.seq = fm1.seq; 
	   fm.ack = fm1.ack;
	   fm.info = fm1.info;
   }


/*===========================================================================*
 	implement your Protocol Variables and Methods below: 
 *==========================================================================*/

 	  private boolean no_nak = true; //initialise noNak, allow sending of Nak
 	  private Timer[] timer = new Timer[NR_BUFS]; //Timer array to time the retransmission
    private Timer ack_timer = new Timer(); //ack_timer to time the piggybacking

    /**
      check if b falls in window with size from a to c
      @param a   int   window lower bound
      @param b   int   frame sequence number
      @param c   int   window upper bound
      @return  boolean  true if b falls in window from a to c
      */
    private boolean between(int a, int b, int c){
      return (((a <= b) && (b < c)) || ((c < a) && (a <= b)) || ((b < c) && (c < a)));
   	}

    /**
      process frames sent to Physical layer
      @param fk   int   frame kind == 0(data), 1(acknowledgement), 2(nak)
      @param frame_num  int   sequence of sending frame in sending window
      @param frame_expected   int   sequence number of frame currently expected in receiving window
      @param buffer   Packet[]  in_buf(receiving window) or out_buf(sending window)
      */
   	void send_frame(int fk, int frame_num, int frame_expected, Packet buffer[]){
      PFrame s = new PFrame(); 
      s.kind = fk;  /*assign frame type*/

      if (fk == PFrame.DATA){
         s.info = buffer[frame_num % NR_BUFS]; /*copy data into frame for transmission*/
         start_timer(frame_num); /*if frame/ack lost, retransmit*/
      }
      s.seq = frame_num; /*assign sequence number (current position in sending window)*/
      s.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);  /*assign ack number (latest frame received in receiving window)*/

      if (fk == PFrame.NAK){
         no_nak = false; /*prohibit any other nak frames to be sent*/
      }

      to_physical_layer(s); /*transmit the frame*/

      stop_ack_timer(); /*ack has sent, no piggybacking needed*/
   }

    /**
      increase an integer within a cycle of 8
      @param i  int 
      @return int  integer after the increase
      */
   private int inc(int i) {
        return (i + 1) % (MAX_SEQ + 1);
    }

    /**
      main part of the protocol
      */
   public void protocol6() {
   		int ack_expected = 0; /*lower bound of the sending window*/ 
    	int next_frame_to_send = 0; /*upper bound of the sending window*/ 
    	int frame_expected = 0; /*lower bound of the receiving window*/ 
    	int too_far = NR_BUFS;  /*upper bound of the receiving window*/ 
      PFrame r = new PFrame(); /* frame to be received from network layer*/

        Packet in_buf[] = new Packet[NR_BUFS]; /*receiving frame of size 4*/
         boolean arrived[] = new boolean[NR_BUFS]; /*in case of duplicate transmission*/
        for (int i=0; i < NR_BUFS; i++){ /*initialise arrived array as all false*/
         	arrived[i] = false;
        }

        enable_network_layer(NR_BUFS); /*load first four frames*/
        init(); /*initialise out_buf*/

	while(true) {	
        wait_for_event(event);
	    switch(event.type) {
	      case (PEvent.NETWORK_LAYER_READY): /*to send frame*/
            from_network_layer(out_buf[next_frame_to_send%NR_BUFS]); /*load frame to sending window*/
            send_frame(PFrame.DATA,next_frame_to_send,frame_expected,out_buf); 
            next_frame_to_send = inc(next_frame_to_send); /*shift load position to right by 1*/
            break; 
	       case (PEvent.FRAME_ARRIVAL): /*process received frame*/
               from_physical_layer(r); 
               if (r.kind == PFrame.DATA){ /*process data frame*/
                  if ((r.seq != frame_expected) && no_nak){ /*frame of wrong sequence received*/
                     send_frame(PFrame.NAK, 0, frame_expected, out_buf);
                  } else {
                     start_ack_timer(); 
                  }

                  if (between(frame_expected, r.seq, too_far) && (arrived[r.seq % NR_BUFS] == false)){ /*receive the expecting frame first time*/
                     arrived[r.seq % NR_BUFS] = true; 
                     in_buf[r.seq % NR_BUFS] = r.info; /*load frame data*/ 
                     while(arrived[frame_expected % NR_BUFS]){ /*transmit all frames at hand to Network layer*/
                        to_network_layer(in_buf[frame_expected % NR_BUFS]);
                        no_nak = true; /*reset no_nak*/
                        arrived[frame_expected % NR_BUFS] = false; /*reset arrived array*/
                        frame_expected = inc(frame_expected); /*expect the next frame*/
                        too_far = inc(too_far); /*expand upper bound of receiving window*/
                        start_ack_timer(); /*wait for piggybacking*/
                     }
                  }
               }

               if ((r.kind == PFrame.NAK) && between(ack_expected, (r.ack + 1) % (MAX_SEQ + 1), next_frame_to_send)){ /*process Nak frame*/
                  send_frame(PFrame.DATA, (r.ack + 1) % (MAX_SEQ + 1), frame_expected, out_buf); /*send Nak frame indicating the lastest frame received, requestin for the next*/
               }               
               while (between(ack_expected, r.ack, next_frame_to_send)){ /*handle piggybacked ack*/
                  stop_timer(ack_expected % NR_BUFS); /*ack received, donot need retransmission*/
                  ack_expected = inc(ack_expected); /*advance lower edge of sending window*/
                  enable_network_layer(1); /*load the next frame from network layer*/
               }
               break;     
            case (PEvent.CKSUM_ERR): /*damaged file*/
               if (no_nak) {
                  send_frame(PFrame.NAK, 0, frame_expected, out_buf); /*send nak file for the latest frame received*/
               }
               break;  
            case (PEvent.TIMEOUT): /*retransmission timer time out*/
               send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf); /*retransmit*/
               break; 
            case (PEvent.ACK_TIMEOUT): /*ack timer time out*/
               send_frame(PFrame.ACK, 0, frame_expected, out_buf); /*send seperate ack frame*/
               break; 
            default: 
               System.out.println("SWP: undefined event type = " + event.type); 
               System.out.flush();
         }
      }
   }

 /* Note: when start_timer() and stop_timer() are called, 
    the "seq" parameter must be the sequence number, rather 
    than the index of the timer array, 
    of the frame associated with this timer, 
   */
 
   private void start_timer(int seq) {
	    stop_timer(seq); 
      		timer[seq % NR_BUFS] = new Timer();
      		timer[seq % NR_BUFS].schedule(new TimerTask(){
         		@Override
         		public void run() {
            		swe.generate_timeout_event(seq);
         		}
      		}, 50);
   }

   private void stop_timer(int seq) {
   		if (timer[seq % NR_BUFS] != null){
         	timer[seq % NR_BUFS].cancel();
     	}
   }

   private void start_ack_timer( ) {
      	stop_ack_timer();  
      		ack_timer = new Timer();
      		ack_timer.schedule(new TimerTask() {
         		@Override
         		public void run(){
            		swe.generate_acktimeout_event();
         		}
      		}, 20); /*ack timer duration is shorter than retransmission timer. 
                    It is to ensure retransmission is not caused by no piggybacking*/
   }

   private void stop_ack_timer() {
     	if (ack_timer != null) {
         ack_timer.cancel();
      	}
   }

}//End of class

/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/


