package scheduler_GPG;

import java.util.ArrayList;

import goalplantree.TreeNode;

public class Decision_GPG {
	
    public ArrayList<TreeNode> actionChoice;
    public boolean forcedPass;

    public Decision_GPG(ArrayList<TreeNode> actionChoice, boolean forcedPass)
    {
    	this.actionChoice = actionChoice;
    	this.forcedPass = forcedPass;
    }
}
