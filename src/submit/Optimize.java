package submit;

import java.util.List;
import joeq.Compiler.Quad.*;
import joeq.Class.jq_Class;
import joeq.Main.Helper;

import submit.optimizations.RemoveRedundantNullChecks;
import submit.optimizations.RemoveRedundantBoundsChecks;
import submit.optimizations.CopyPropagation;
import submit.optimizations.RemoveDeadCode;
import submit.optimizations.PartialRedundancyElimination;
import submit.optimizations.Optimization;
import submit.optimizations.ConstantPropagation;

public class Optimize {
    /*
     * optimizeFiles is a list of names of class that should be optimized
     * if nullCheckOnly is true, disable all optimizations except "remove redundant NULL_CHECKs."
     */
    public static void optimize(List<String> optimizeFiles, boolean nullCheckOnly) {
        for (int i = 0; i < optimizeFiles.size(); i++) {
            jq_Class classToOptimize = (jq_Class)Helper.load(optimizeFiles.get(i));
            // Run your optimization on each classes.

            if (nullCheckOnly){
                RemoveRedundantNullChecks redundantNullChecks = new RemoveRedundantNullChecks();
                redundantNullChecks.optimizeClass(classToOptimize);
            }
            else
            {
                boolean modified = false;

                Optimization copyPropagation = new CopyPropagation();
                Optimization redundantNullChecks = new RemoveRedundantNullChecks();
                Optimization deadCode = new RemoveDeadCode();
                Optimization pre = new PartialRedundancyElimination();
                Optimization constantPropagation = new ConstantPropagation();
                Optimization boundsChecks = new RemoveRedundantBoundsChecks();

                do {

                    //System.out.println("**************** Optimization pass *****************");

                    modified = false;

                    if (constantPropagation.optimizeClass(classToOptimize)){
                        //System.out.println("constant prop modified the graph");
                        modified = true;
                    }

                    if (deadCode.optimizeClass(classToOptimize)){
                        //System.out.println("dead modified the graph");
                        modified = true;
                    }

                    if (pre.optimizeClass(classToOptimize)){
                        //System.out.println("pre modified the graph");
                        modified = true;
                    }

                    if (redundantNullChecks.optimizeClass(classToOptimize)){
                        //System.out.println("null checks modified the graph");
                        modified = true;
                    }

                    if (copyPropagation.optimizeClass(classToOptimize)){
                        //System.out.println("copy prop modified the graph");
                        modified = true;
                    }
                    
                    if (boundsChecks.optimizeClass(classToOptimize)){
                        //System.out.println("bounds checks modified the graph");
                    	modified = true;
                    }
                   
                    //Helper.runPass(classToOptimize, new PrintCFG());

                } while(modified);
            }

            //Helper.runPass(classToOptimize, new PrintCFG());
        }
    }
}
