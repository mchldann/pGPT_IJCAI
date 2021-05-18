package scheduler_GPG;

import java.util.HashSet;

import goalplantree.TreeNode;

public class Match_Info {
	
	public State_GPG final_state;
	public HashSet<TreeNode> nodes_visited;
	
    public Match_Info(State_GPG final_state, HashSet<TreeNode> nodes_visited)
    {
    	this.final_state = final_state;
    	this.nodes_visited = nodes_visited;
    }
}
