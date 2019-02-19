package submit.optimizations;

import flow.Flow;
import java.util.*;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.*;

import submit.analyses.MySolver;
import submit.analyses.AnticipatedExpressions;
import submit.analyses.AnticipatedExpressions.*;
import submit.analyses.AvailableExpressions;
import submit.analyses.AvailableExpressions.*;
import submit.analyses.Postponable;
import submit.analyses.Postponable.*;

public class PartialRedundancyElimination extends Optimization {

    public boolean supportLastQuad(BasicBlock bb){
        Quad lastQuad = bb.getLastQuad();
        if (lastQuad != null) {
            Operator op = lastQuad.getOperator();
            if (op instanceof Operator.Branch && !(op instanceof Operator.Ret)){
                if ((op instanceof Operator.LookupSwitch) || (op instanceof Operator.TableSwitch)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Add edge from pre -> suc
    public void addEdge(BasicBlock pre, BasicBlock suc, boolean fallthrough) {
        List<BasicBlock> preSuccessors      = pre.getSuccessors();
        List<BasicBlock> sucPredecessors    = suc.getPredecessors();

        System.out.println("adding edge " + pre + " -> " + suc);

        if (fallthrough) {
            preSuccessors.add(0, suc);
            sucPredecessors.add(0, pre);
        }else{
            preSuccessors.add(suc);
            sucPredecessors.add(pre);
        }

        // Fix the branch quad if there is one
        Quad lastQuad = pre.getLastQuad();
        if (lastQuad != null)
        {
            Operator op = lastQuad.getOperator();
            if (op instanceof Operator.Branch && !(op instanceof Operator.Ret)){
                Operand.TargetOperand newTarget = new Operand.TargetOperand(suc);

                if (op instanceof Operator.Goto){
                    Operator.Goto.setTarget(lastQuad, newTarget);
                }else if (op instanceof Operator.IntIfCmp){
                    Operator.IntIfCmp.setTarget(lastQuad, newTarget);
                }else if (op instanceof Operator.Jsr){
                    Operator.Jsr.setTarget(lastQuad, newTarget);
                }else if (op instanceof Operator.Jsr){
                    Operator.Jsr.setTarget(lastQuad, newTarget);
                }

                System.out.println(lastQuad);  
            }
        }
    }

    // Remove edge from pre -> suc, return true if it was a fallthrough edge
    public boolean removeEdge(BasicBlock pre, BasicBlock suc) {
        List<BasicBlock> preSuccessors      = pre.getSuccessors();
        List<BasicBlock> sucPredecessors    = suc.getPredecessors();

        boolean fallthrough = (preSuccessors.indexOf(suc) == 0);

        preSuccessors.remove(suc);
        sucPredecessors.remove(pre);

        return fallthrough;
    }

    public void visitCFG(ControlFlowGraph cfg) {

        //System.out.println("Method: "+cfg.getMethod().getName().toString());

        System.out.println("Method: "+cfg.getMethod().getName().toString());

        MySolver solver = new MySolver();
        AnticipatedExpressions anticipation = new AnticipatedExpressions();
        AvailableExpressions available = new AvailableExpressions();
        Postponable postponable = new Postponable();
        Set<Integer> nopQuadsToRemove = new TreeSet<Integer>();

        System.out.println(cfg.fullDump());

        System.out.println("adding NOPs");

        // Preprocess graph adding basic blocks (wiht NOP quads which wil be removed later) on quads with multiple predcessors
        ListIterator<BasicBlock> bbit = cfg.reversePostOrderIterator();
        while (bbit.hasNext())
        {
            BasicBlock bb = bbit.next();
            
            if (bb.getNumberOfPredecessors() > 1) {
                List<BasicBlock> pre = bb.getPredecessors();
                List<BasicBlock> predcessorsTemp = new Vector<BasicBlock>(pre);

                List<BasicBlock> newPredecessors = new Vector<BasicBlock>();
                
                for (BasicBlock oldPred : predcessorsTemp) {

                    System.out.println(predcessorsTemp);

                    if (supportLastQuad(oldPred)){

                        // Create NOP block
                        BasicBlock newBB = cfg.createBasicBlock(1, 1, 1, null);
                        Quad nopQuad = Operator.Special.create(cfg.getNewQuadID(), Operator.Special.NOP.INSTANCE);
                        nopQuadsToRemove.add(nopQuad.getID());
                        newBB.appendQuad(nopQuad);

                        boolean fallthrough = removeEdge(oldPred, bb);
                        addEdge(oldPred, newBB, fallthrough);
                        addEdge(newBB, bb, fallthrough);
                    }
                }
            }
        }

        System.out.println(cfg.fullDump());

        solver.registerAnalysis(anticipation);
        solver.visitCFG(cfg);

        available.registerAnticipated(anticipation);
        solver.registerAnalysis(available);
        solver.visitCFG(cfg);

        // Create earliest set
        Set[] earliest = new Set[cfg.getMaxQuadID() + 1];
        QuadIterator qit = new QuadIterator(cfg);
        while (qit.hasNext())
        {
            Quad q = qit.next();
            AnticipatedSet anticipationIn = (AnticipatedSet) anticipation.getIn(q);
            AvailableSet availableIn = (AvailableSet) available.getIn(q);
            Set<String> anticipationInSet = anticipationIn.getSet();
            Set<String> availableInSet = availableIn.getSet();
            anticipationInSet.removeAll(availableInSet);
            earliest[q.getID()] = anticipationInSet;
        }

        postponable.registerEarliest(earliest);
        solver.registerAnalysis(postponable);
        solver.visitCFG(cfg);

        // Create latest set  
        Set[] latest = new Set[cfg.getMaxQuadID() + 1];
        qit = new QuadIterator(cfg);
        while (qit.hasNext())
        {
            Quad q = qit.next();
            PostponableSet postponableIn = (PostponableSet) postponable.getIn(q);
            Set<String> postponableInSet = postponableIn.getSet();
            Set<String> useSet = new TreeSet<String>();
            Set<String> successorSet = new TreeSet<String>();

            if (Postponable.isValidExpression(q)) {
                useSet.add(Postponable.expressionString(q));
            }

            Iterator<Quad> suc = qit.successors();
            while(suc.hasNext()){
                Quad qp = suc.next();

                PostponableSet sucPostponableIn;
                Set<String> sucPostponableInSet;

                if (qp != null){
                    sucPostponableIn = (PostponableSet) postponable.getIn(q);
                    sucPostponableInSet = sucPostponableIn.getSet();     

                    successorSet.addAll(earliest[qp.getID()]);  
                }else{
                    sucPostponableIn = (PostponableSet) postponable.getExit();
                    sucPostponableInSet = sucPostponableIn.getSet();      
                }

                successorSet.addAll(sucPostponableInSet);
            }


            Set<String> successorSetComplement = new TreeSet<String>(postponable.universalSet);
            successorSetComplement.removeAll(successorSet);

            Set<String> intersectionSet = new TreeSet<String>(successorSetComplement);
            intersectionSet.addAll(useSet);

            latest[q.getID()] = earliest[q.getID()];
            latest[q.getID()].addAll(postponableInSet);
            latest[q.getID()].retainAll(intersectionSet);

            //System.out.println(latest[q.getID()]);
        }

        // Remove added NOP commands
        System.out.println("removing NOPs");
        qit = new QuadIterator(cfg);
        while (qit.hasNext()) {
            Quad q = qit.next();
            if (nopQuadsToRemove.contains(q.getID())) {
                qit.remove();
            }
        }

        // Remove empty basic blocks
        bbit = cfg.reversePostOrderIterator();
        while (bbit.hasNext())
        {
            BasicBlock bb = bbit.next();

            if (    bb.size() == 0 
                && !bb.equals(cfg.entry())
                && !bb.equals(cfg.exit())) 
            {
                List<BasicBlock> pre = new Vector<BasicBlock>(bb.getPredecessors());
                List<BasicBlock> suc = new Vector<BasicBlock>(bb.getSuccessors());

                for (BasicBlock prebb : pre) {
                    boolean fallthrough = removeEdge(prebb, bb);
                    for (BasicBlock sucbb : suc) {
                        addEdge(prebb, sucbb, fallthrough);
                    }
                }

                for (BasicBlock sucbb : suc) {
                    removeEdge(bb, sucbb);
                }
            }
        }  
        cfg.removeUnreachableBasicBlocks();   

        System.out.println(cfg.fullDump());  
    }
}