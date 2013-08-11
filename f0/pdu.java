//pdu.java, version 090727, www.fredrikolofsson.com
//built during my summer residency at european bridges ensemble, hamburg 2009
//released under gnu gpl license

package f0;

import java.io.*;
import java.util.ArrayList;
import com.cycling74.max.*;

public class pdu extends MaxObject {
	private int smscLength;
	private int smscType;
	private String smscNumber;
	private int msgType;
	private int addrLength;
	private int addrType;
	private String sendNumber;
	private int pid;
	private int dcs;
	private String date, time, zone;
	private int msgLength;
	private String msgText;
	private static final String[] INLET_ASSIST = new String[]{
		"decode pdu-string"
	};
	private static final String[] OUTLET_ASSIST = new String[]{
		"decoded pdu data"
	};
	public pdu(Atom[] args) {
		declareTypedIO("M", "A");
		createInfoOutlet(false);
		setInletAssist(INLET_ASSIST);
		setOutletAssist(OUTLET_ASSIST);
	}
	public void reset() {
		smscLength= -1;
		smscType= -1;
		smscNumber= "?";
		msgType= -1;
		addrLength= -1;
		addrType= -1;
		sendNumber= "?";
		pid= -1;
		dcs= -1;
		date= "?";
		time= "?";
		zone= "?";
		msgLength= -1;
		msgText= "?";
	}
	public void decode(String str) {
		reset();
		StringReader sr= new StringReader(str);
		
		smscLength= readInt(sr);
		smscType= readInt(sr);
		smscNumber= readStr(sr, smscLength-1);
		if((smscType&112)==16) {
			smscNumber= "+"+smscNumber;			//international number
		}
		
		msgType= readInt(sr);
		if(msgType==17) {						//skipping
			try{
				sr.read();
				sr.read();
			} catch(Exception e) {
				error("pdu - decode 1: "+e);
			}
		}
		addrLength= readInt(sr);
		addrType= readInt(sr);
		sendNumber= readStr(sr, (int)Math.ceil(addrLength/2.0));
		if(((addrType&112)==16)||((addrType&120)==8)) {
			sendNumber= "+"+sendNumber;			//international number
		}
		
		pid= readInt(sr);
		dcs= readInt(sr);
		if(msgType==17) {
			try{
				sr.read();
				sr.read();
			} catch(Exception e) {
				error("pdu - decode 2: "+e);
			}
		} else {
			date= readStr(sr, 1)+"-"+readStr(sr, 1)+"-"+readStr(sr, 1);
			time= readStr(sr, 1)+":"+readStr(sr, 1)+":"+readStr(sr, 1);
			zone= readGmt(sr);
		}
		
		msgLength= readInt(sr);
		if((msgType&64)==64) {					//remove header if TP-UDHI
			for(int i= 0; i<14; i++) {
				try{
					sr.read();
				} catch(Exception e) {
					error("pdu - decode 0: "+e);
				}
			}
		}
		msgText= readTxt(sr, msgLength);
		
		outlet(0, "smscLength", smscLength);
		outlet(0, "smscType", smscType);
		outlet(0, "smscNumber", smscNumber);
		outlet(0, "msgType", msgType);
		outlet(0, "addrLength", addrLength);
		outlet(0, "addrType", addrType);
		outlet(0, "sendNumber", sendNumber);
		outlet(0, "pid", pid);
		outlet(0, "dcs", dcs);
		outlet(0, "date", date);
		outlet(0, "time", time);
		outlet(0, "zone", zone);
		outlet(0, "msgLength", msgLength);
		outlet(0, "msgText", msgText);
	}
	private String readTxt(StringReader sr, int len) {
		ArrayList binary8= new ArrayList();
		ArrayList binary7= new ArrayList();
		String tmp= "";
		String res= "";
		int cnt= 0;
		int a;
		String b;
		try{
			while((a= readInt(sr)) != -1) {
				b= toBinaryString(a);
				binary8.add(b);
				if(cnt%7==6) {
					binary8.add("");
				}
				cnt= cnt+1;
			}
			for(int i= 0; i<binary8.size(); i++) {
				String x= (String)binary8.get(i);
				if(x.length()>0) {
					try{
						binary7.add(x.substring((i%8)+1, 8));
						
					} catch(Exception e) {
						error("pdu - readText 1: "+e);
					}
				}
				if((i%8)!=0) {
					String y= (String)binary8.get(i-1);
					try{
						binary7.add(y.substring(0, i%8));
					} catch(Exception e) {
						error("pdu - readTxt 2: "+e);
					}
				}
			}
			for(int i= 0; i<binary7.size(); i++) {
				tmp= tmp+(String)binary7.get(i);
			}
			for(int i= 0; i<(tmp.length()/7); i++) {
				try{
					String z= tmp.substring(i*7, (i*7)+7);
					char chr= (char)Integer.parseInt(z, 2);
					if(chr!=0) {
						res= res+chr;
					}
				} catch(Exception e) {
					error("pdu - readTxt 3: "+e);
				}
			}
		} catch(Exception e) {
			error("pdu - readTxt 0: "+e);
		}
		return res;
	}
	private String readStr(StringReader sr, int len) {
		String res= "";
		for(int i= 0; i<len; i++) {
			try{
				int a= sr.read();
				int b= sr.read();
				res= res+(b-'0');
				if(a!='F') {
					res= res+(a-'0');
				}
			} catch(Exception e) {
				error("pdu - readStr 0: "+e);
			}
		}
		return res;
	}
	private String readGmt(StringReader sr) {
		String res= "";
		try{
			int a= fromHexDigit(sr.read());
			int b= fromHexDigit(sr.read());
			if(((a&8)==8)||((b&8)==8)) {
				res= "GMT-"+((((b&7)*10)+(a&7))*15/60);
			} else {
				res= "GMT+"+(((b*10)+a)*15/60);
			}
		} catch(Exception e) {
			error("pdu - readGmt 0: "+e);
		}
		return res;
	}
	private String readHex(StringReader sr) {
		String res= "";
		try{
			res= res+(char)sr.read()+(char)sr.read();
		} catch(Exception e) {
			error("pdu - readHex 0: "+e);
		}
		return res;
	}
	private int readInt(StringReader sr) {
		int a, b, res= -1;
		try{
			a= fromHexDigit(sr.read());
			if(a==-1) {return -1;}
			b= fromHexDigit(sr.read());
			if(b==-1) {return -1;}
			res= (a*16)+b;
		} catch(Exception e) {
			error("pdu - readInt 0: "+e);
		}
		return res;
	}
	private int fromHexDigit(int chr) {
		if(chr>='0' && chr<='9') {
			return chr-'0';
		} else if(chr>='A' && chr<='F') {
			return 10+chr-'A';
		}
		return -1;
	}
	private String toBinaryString(int val) {
		String digits= "0123456789";
		String res= "";
		while(val>0) {
			int d= val%2;
			res= digits.charAt(d)+res;
			val= val/2;
		}
		while(res.length()<8) {
			res= "0"+res;
		}
		return res;
	}
}



