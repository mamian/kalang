package kava.opcode.op;
import kava.opcode.Op;

public class CMP extends Op {
        public kava.opcode.VarObject result;
        public kava.opcode.VarObject v1;
        public kava.opcode.VarObject v2;
    public CMP(kava.opcode.VarObject result,kava.opcode.VarObject v1,kava.opcode.VarObject v2){
            this.result = result;
            this.v1 = v1;
            this.v2 = v2;
    
    }
    public Object getParameter(String key){
        if(key == "result") return this.result;
        if(key == "v1") return this.v1;
        if(key == "v2") return this.v2;
        return null;
    }
    public void setParameter(String key,Object value){
        if(key == "result"){this.result = (kava.opcode.VarObject)value;return;}
        if(key == "v1"){this.v1 = (kava.opcode.VarObject)value;return;}
        if(key == "v2"){this.v2 = (kava.opcode.VarObject)value;return;}
    }
}