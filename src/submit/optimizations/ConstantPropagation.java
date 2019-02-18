package submit.optimizations;

import flow.Flow;
import java.util.*;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.IConstOperand;
import joeq.Compiler.Quad.*;

import submit.analyses.MySolver;
import submit.analyses.ConstantProp;
import submit.analyses.ConstantProp.*;

public class ConstantPropagation extends Optimization {

    public void visitCFG(ControlFlowGraph cfg) {

        MySolver solver = new MySolver();
        ConstantProp constantProp = new ConstantProp();
        solver.registerAnalysis(constantProp);
        solver.visitCFG(cfg);

        // Generate copies map
        QuadIterator qit = new QuadIterator(cfg);
        while (qit.hasNext())
        {
            Quad q = (Quad)qit.next();

            ConstantPropTable ct = (ConstantPropTable) constantProp.getIn(q);
			
			if (q.getOperator() instanceof Operator.Move)
			{
				Operand op = (RegisterOperand) Operator.Move.getSrc(q);
				
				if (op instanceof RegisterOperand)
				{
					RegisterOperand regop = (RegisterOperand) op;
					
					String key = regop.getRegister().toString();
					
					SingleCP scp = (SingleCP) ct.get(key);
					
					if (scp.isConst())
					{
						IConstOperand c = new IConstOperand(scp.getConst());
						Operator.Move.setSrc(q, c);
					}
				}
			}
			else if (q.getOperator() instanceof Operator.Binary)
			{
				Operand opone = Operator.Binary.getSrc1(q);
				Operand optwo = Operator.Binary.getSrc2(q);
				
				if (opone instanceof RegisterOperand)
				{
					RegisterOperand regopone = (RegisterOperand) opone;
					
					String keyone = regopone.getRegister().toString();
					SingleCP scpone = (SingleCP) ct.get(keyone);
					
					if (scpone.isConst())
					{
						IConstOperand c = new IConstOperand(scpone.getConst());
						Operator.Binary.setSrc1(q, c);
					}
					
				}
				
				if (optwo instanceof RegisterOperand)
				{
					RegisterOperand regoptwo = (RegisterOperand) optwo;
					
					String keytwo = regoptwo.getRegister().toString();
					SingleCP scptwo = (SingleCP) ct.get(keytwo);
					
					if (scptwo.isConst())
					{
						IConstOperand c = new IConstOperand(scptwo.getConst());
						Operator.Binary.setSrc2(q, c);
					}
				}
			}
			else if (q.getOperator() instanceof Operator.Unary)
			{
				Operand op = (RegisterOperand) Operator.Unary.getSrc(q);
				
				if (op instanceof RegisterOperand)
				{
					RegisterOperand regop = (RegisterOperand) op;
					
					String key = regop.getRegister().toString();
					
					SingleCP scp = (SingleCP) ct.get(key);
					
					if (scp.isConst())
					{
						IConstOperand c = new IConstOperand(scp.getConst());
						Operator.Unary.setSrc(q, c);
					}
				}
			}
        }
    }
}