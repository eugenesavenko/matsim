package playground.david.vis.data;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;

import playground.david.vis.OnTheFlyServer;

public abstract class OTFDataWriter<SrcData> implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7593448140900220038L;
	
	static public transient OnTheFlyServer server = null;
	protected transient SrcData src;
	
	abstract public void writeConstData(ByteBuffer out) throws IOException;
	abstract public void writeDynData(ByteBuffer out) throws IOException;
	
	public void setSrc(SrcData src) {
		this.src = src;
	}

}
