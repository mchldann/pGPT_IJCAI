package scheduler_GPG;

import java.util.ArrayList;

import goalplantree.TreeNode;

public class Pass_Scheduler_GPG extends Scheduler_GPG {

	@Override
	public void reset()
	{
		// Nothing to reset for this scheduler
	}
    
    public Decision_GPG getDecision(State_GPG state)
    {
    	ArrayList<TreeNode> actionChoices = new ArrayList<TreeNode>();	
    	actionChoices.add(null);
    	return new Decision_GPG(actionChoices, true);
    }
}
