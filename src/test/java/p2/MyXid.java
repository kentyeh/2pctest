/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package p2;

import java.nio.ByteBuffer;
import javax.transaction.xa.Xid;

/**
 *
 * @author kent
 */
public class MyXid implements Xid {

    protected int formatId;
    protected byte gtrid[];
    protected byte bqual[];

    public MyXid(int formatId, int gtrid, int bqual) {
        this.formatId = formatId;
        this.gtrid = ByteBuffer.allocate(64).putInt(gtrid).array();
        this.bqual = ByteBuffer.allocate(64).putInt(bqual).array();
    }

    public MyXid(int formatId, byte gtrid[], byte bqual[]) {
        this.formatId = formatId;
        this.gtrid = gtrid;
        this.bqual = bqual;
    }

    @Override
    public int getFormatId() {
        return formatId;
    }

    @Override
    public byte[] getBranchQualifier() {
        return bqual;
    }

    @Override
    public byte[] getGlobalTransactionId() {
        return gtrid;
    }
}
