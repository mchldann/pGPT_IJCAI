package goalplantree;

import beliefbase.Condition;

public class PlanTemplate {
    final public Condition[] prec;
    final public Condition[] inc;
    final public Condition[] postc;
    final public TreeNode[] body;
    final String name;

    public PlanTemplate(String n, Condition[] precondition, Condition[] incondition, Condition[] postconditions, TreeNode[] planbody){
        this.prec = precondition;
        this.inc = incondition;
        this.postc = postconditions;
        this.body = planbody;
        this.name = n;
    }
}