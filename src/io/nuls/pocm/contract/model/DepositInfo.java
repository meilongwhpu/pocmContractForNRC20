package io.nuls.pocm.contract.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static io.nuls.contract.sdk.Utils.require;

/**
 * 抵押信息
 * @author: Long
 * @date: 2019-03-15
 */
public class DepositInfo {

    //抵押者地址
    private String  depositorAddress;

    // 抵押金额
    private BigInteger depositTotalAmount;

    //抵押笔数
    private int depositCount;

    //抵押详细信息列表
    private List<DepositDetailInfo> depositDetailInfos = new ArrayList<DepositDetailInfo>();

    public DepositInfo(){
        this.depositTotalAmount=BigInteger.ZERO;
        this.depositCount=0;
    }

    public DepositInfo(DepositInfo info){
        this.depositorAddress=info.depositorAddress;
        this.depositTotalAmount=info.depositTotalAmount;
        this.depositCount=info.depositCount;
        this.depositDetailInfos=info.depositDetailInfos;
    }

    public BigInteger getDepositTotalAmount() {
        return depositTotalAmount;
    }

    public void setDepositTotalAmount(BigInteger depositTotalAmount) {
        this.depositTotalAmount = depositTotalAmount;
    }

    public List<DepositDetailInfo> getDepositDetailInfos() {
        return depositDetailInfos;
    }

    public void setDepositDetailInfos(List<DepositDetailInfo> depositDetailInfos) {
        this.depositDetailInfos = depositDetailInfos;
    }


    public int getDepositCount() {
        return depositCount;
    }

    public void setDepositCount(int depositCount) {
        this.depositCount = depositCount;
    }


    public String getDepositorAddress() {
        return depositorAddress;
    }

    public void setDepositorAddress(String depositorAddress) {
        this.depositorAddress = depositorAddress;
    }

    /**
     * 根据抵押编号获取抵押详细信息
     * @param depositNumber
     * @return
     */
    public DepositDetailInfo getDepositDetailInfoByNumber(long depositNumber){
        DepositDetailInfo info=null;
        for (int i=0;i<depositDetailInfos.size();i++) {
            info=depositDetailInfos.get(i);
           if(info.getDepositNumber()==depositNumber){
               break;
           }
        }
        require(info != null, "未找到此抵押编号的抵押详细信息");
        return info;
    }

    /**
     *
     * @param depositNumber
     */
    public void removeDepositDetailInfoByNumber(long depositNumber){
        for (int i=0;i<depositDetailInfos.size();i++) {
            if(depositDetailInfos.get(i).getDepositNumber()==depositNumber){
                depositDetailInfos.remove(i);
                break;
            }
        }
    }

    public void clearDepositDetailInfos(){
        depositDetailInfos.clear();
        depositCount=0;
        depositTotalAmount=BigInteger.ZERO;
    }


    @Override
    public String toString(){
        return  "{depositTotalAmount:"+depositTotalAmount+",depositorAddress:"+depositorAddress
                +",depositCount:"+depositCount+",depositDetailInfos:"+convertListToString()+"}}";
    }

    private  String convertListToString(){
        String detailinfo ="{";
        String temp="";
        for(int i=0;i<depositDetailInfos.size();i++){
            DepositDetailInfo detailInfo= depositDetailInfos.get(i);
            temp =detailInfo.toString();
            detailinfo=detailinfo+temp+",";
        }
        detailinfo=detailinfo.substring(0,detailinfo.length()-1);
        detailinfo=detailinfo+"}";
        return detailinfo;
    }
}
