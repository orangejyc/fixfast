package stepparse;

import static falcon.fix.MessageTypes.ExecutionReport;
import static falcon.fix.MessageTypes.Heartbeat;
import static falcon.fix.MessageTypes.Logon;
import static falcon.fix.MessageTypes.Logout;
import static falcon.fix.MessageTypes.NewOrderSingle;
import static falcon.fix.MessageTypes.Reject;
import static falcon.fix.MessageTypes.ResendRequest;
import static falcon.fix.MessageTypes.SequenceReset;
import static falcon.fix.MessageTypes.TestRequest;
import static stepparse.StepTags.BeginString;
import static stepparse.StepTags.BodyLength;
import static stepparse.StepTags.CheckSum;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.openfast.ByteUtil;
import org.openfast.Message;
import org.openfast.MessageInputStream;
import org.openfast.codec.FastDecoder;
import org.openfast.template.loader.XMLMessageTemplateLoader;

import falcon.fix.ByteString;
import falcon.fix.Field;
import falcon.fix.MessageType;
import falcon.fix.ParseException;
import falcon.fix.Protocol;

public class ParseSzseSteps {

	static InputStream templateSource = null;
	static XMLMessageTemplateLoader templateLoader = null;

	public ParseSzseSteps() {
		try {
			templateSource = new FileInputStream("data\\fast_template_STEP1.20_SZ_1.00.xml");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		templateLoader = new XMLMessageTemplateLoader();
		templateLoader.setLoadTemplateIdFromAuxId(true);
		templateLoader.load(templateSource);
		
		
		
	}

	private static int sum(ByteBuffer buf) {
		return sum(buf, 0, buf.position());
	}

	private static int sum(ByteBuffer buf, int start, int end) {
		int result = 0;
		for (int i = start; i < end; i++)
			result += buf.get(i);
		return result;
	}

	private static long sumlong(ByteBuffer buf, int start, int end) {
		long result = 0;
		int tmp = 0;
		for (int i = start; i < end; i++) {
			tmp = buf.get(i) & 0xFF;
			result += tmp;
		}

		return result;
	}

	public static MessageType getMsgType(ByteBuffer buf) throws ParseException {

		Protocol.matchTag(buf, StepTags.MsgType);

		ByteString value = Protocol.parseString(buf, (byte) 0x01);

		MessageType result = null;
		String type = new String(value.getData());
		// System.out.println("the msg type is: " + type);
		result = new MessageType(type);
		// switch (buf.get()) {
		// case (byte)'0': result = Heartbeat; break;
		// case (byte)'1': result = TestRequest; break;
		// case (byte)'2': result = ResendRequest; break;
		// case (byte)'3': result = Reject; break;
		// case (byte)'4': result = SequenceReset; break;
		// case (byte)'5': result = Logout; break;
		// case (byte)'8': result = ExecutionReport; break;
		// case (byte)'A': result = Logon; break;
		// case (byte)'D': result = NewOrderSingle; break;
		// case (byte)0x01:
		// throw new ParseFailedException("Invalid MsgType (35)");
		// default:
		// throw new ParseFailedException("Tag specified without a value");
		// }
		// if (buf.get() != (byte)0x01) {
		// throw new ParseFailedException("Invalid MsgType (35)");
		// }
		return result;
	}

	public static void DoParse(falcon.fix.Message s) {

		// parse the W message
		MessageType msg = s.type();
		ByteString raw = null;

		if (msg.value().equalsIgnoreCase("W")) {
			for (Field f : s.fields()) {
				if (f.tag() == 96) {
					raw = (ByteString) f.value();
					ParseSnap(raw);
				}
			}

		}

	}

	public static void ParseSnap(ByteString data) {

		ByteArrayInputStream in = new ByteArrayInputStream(data.getData());

		MessageInputStream mis = new MessageInputStream(in);
		mis.setTemplateRegistry(templateLoader.getTemplateRegistry());

		Message md = null;
		while ((md = mis.readMessage()) != null) {
			System.out.println("the mesage template id is: " + md.getTemplate().getId());
			System.out.println(md.getLong("OrigTime"));			
			System.out.println(md.getInt("ChannelNo"));
			System.out.println(md.getString("MDStreamID"));
			System.out.println(md.getString("SecurityID"));
			System.out.println(md.getString("SecurityIDSource"));
			System.out.println(md.getString("TradingPhaseCode"));
			System.out.println(md.getLong("PreClosePx"));
			System.out.println(md.getLong("NumTrades"));
			System.out.println(md.getLong("TotalVolumeTrade"));
			System.out.println(md.getLong("TotalValueTrade"));
		}

		mis.reset();
	}


	public static void main(String args[]) throws FileNotFoundException, ParseException {

		ParseSzseSteps parse = new ParseSzseSteps();
		
		File origFile = new File("data\\step.dat");
		FileInputStream origStream = new FileInputStream(origFile);

		System.out.println("the file length is: " + origFile.length());
		ByteBuffer buffer = ByteBuffer.allocate((int) origFile.length());

		byte[] readbuf = new byte[(int) origFile.length()];
		int readnum = 0;

		while (true) {
			try {
				readnum = origStream.read(readbuf);

				if (readnum == -1) {
					System.out.println("read finished");
					break;
				}
				buffer.put(readbuf, 0, readnum);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// read fix msg
		MessageType msgType = null;
		int start = 0;
		buffer.flip();
		List<Field> fields = new ArrayList<Field>();
		List<falcon.fix.Message> msgs = new ArrayList<falcon.fix.Message>();    
        while (buffer.remaining() > 0) {
            start = buffer.position();
            Protocol.match(buffer, BeginString);
            int bodyLen = Protocol.matchInt(buffer, BodyLength);
            int msgTypeOffset = buffer.position();
            //得到该条STEP消息的类型
            msgType = getMsgType(buffer);
            int checksumOffset = msgTypeOffset + bodyLen;
            buffer.position(checksumOffset);
            long checksumActual = sumlong(buffer, start, buffer.position()) % 256;
            int checksumExpected = Protocol.matchInt(buffer, CheckSum);
            if (checksumExpected != checksumActual) {
                throw new RuntimeException(
                        String.format("Invalid checksum: expected %d, got: %d", checksumExpected, checksumActual));
            }

            int endOffset = buffer.position();
            // process snapshot message
            if (msgType.value().equalsIgnoreCase("W")) {
                int len = 0;
                int iCount = 0;
                ByteString value = null;
                buffer.position(msgTypeOffset);
                //循环遍历得到该消息中的每个字段(tag)和字段值(value)
                while (buffer.position() < checksumOffset) {
                    int tag = Protocol.parseInt(buffer, (byte) '=');
                    //如果tag=95,得到FAST消息体的长度
                    if (tag == 95) {
                        value = Protocol.parseString(buffer, (byte) 0x01);
                        len = Integer.parseInt(new String(value.getData()));
                        fields.add(new Field(tag, value));
                    }
                    else if (tag == 96) {
                        //如果tag=96,根据之前得到的FAST消息长度length,
                        //将"96="之后的length长度的字节数组拷贝到value中
                        byte[] raw = new byte[len];
                        for (iCount = 0; iCount < len; iCount++) {
                            raw[iCount] = buffer.get();
                        }
                        fields.add(new Field(tag, new ByteString(raw)));
                    }
                    else { 
                        //如果是其他字段,通过delimiter得到value
                        value = Protocol.parseString(buffer, (byte) 0x01);
                        fields.add(new Field(tag, value));    
                    }
                    //跳过delimiter字节
                    buffer.get();
                }
                
                //跳转到下一条STEP消息
                buffer.position(endOffset);
                falcon.fix.Message msg = new falcon.fix.Message(msgType, fields);
                //一条快照消息的类型和字段列表存放到消息列表中
                msgs.add(msg);

            }
        }

		for (falcon.fix.Message s : msgs) {
			DoParse(s);
		}

	}

}
