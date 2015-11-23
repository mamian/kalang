package kalang.ast.stmt

import kalang.ast.expr.AstNode;
import kalang.core.VarTable

class LoopStmt extends Statement {
	public BlockStmt loopBlock = new BlockStmt();
	public AstNode prefixTestExpr
	public AstNode suffixTestExpr
	public VarTable varTable = new VarTable();
	
	String toString(){
		if(this.prefixTestExpr){
			List<String> vars = [];
			for(def v in varTable.toArray()){
				vars.add("${v.getType()} ${v.getName()}")
			}
			String vStr = vars.join(",")
			return "for(${vStr};${this.prefixTestExpr};) ${this.loopBlock}"
		}else{
			return "do ${this.loopBlock}while(${this.prefixTestExpr});"
		}
	}
}