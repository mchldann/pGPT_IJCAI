package goalplantree;

import java.util.ArrayList;

public abstract class TreeNode {

    /**
     * definition of the state
     */
    public enum Status
    {
        // enumerate status
        SUCCESS("success"),
        FAILURE("failure"),
        ACTIVE("active"),
        DEFAULT("default");

        private String name;

        private Status(String name){
            this.name = name;
        }

        public boolean isDone(){
            return this == SUCCESS || this == FAILURE;
        }

        @Override
        public String toString(){
            return this.name;
        }
    };

    /**
     * the parent node
     */
    protected TreeNode parent;

    /**
     * the unique identifier
     */
    protected String id;

    /**
     * the type name
     */
    protected String type;

    /**
     * the execution status
     */
    protected Status status;
    /**
     * the next step of this node
     */
    protected TreeNode next;

    /**
     * prerequisites steps
     */
    protected ArrayList<TreeNode> preqList = new ArrayList<>();
    /**
     * dependent steps
     */
    protected ArrayList<TreeNode> depList = new ArrayList<>();

    protected int intention_num = -1;

    /**
     * indentation
     */
    final String indent = "    ";


    public TreeNode(String id, String type){
        // name
        this.id = id;
        this.type = type;
        init();
    }

    /**
     * initialisation
     */
    private void init(){
        // initially the execution status is set to be default
        this.status = Status.DEFAULT;
    }

    /**
     * @return the name of this node
     */
    public String getId(){
        return this.id;
    }

    /**
     * @return the type name of this goal/plan/action
     */
    public String getType(){
        return this.type;
    }

    /**
     * @return the parent of this node
     */
    public TreeNode getParent(){
        return this.parent;
    }

    public void setParent(TreeNode node){
        this.parent = node;
    }

    /**
     * @return the current status
     */
    public Status getStatus(){
        return this.status;
    }

    /**
     *
     */
    public void setStatus(Status state){
        this.status = state;
    }


    /**
     * @return the next step in the gpt
     */
    public TreeNode getNext(){
        return this.next;
    }
    
    public TreeNode getNextNoOverride(){
        return this.next;
    }

    /**
     * set the next goal/action
     * @param node
     */
    public void setNext(TreeNode node){
        this.next = node;
    }

//    public abstract TreeNode Fail();

    public abstract String onPrintNode(int num);


    /**
     * @return the prerequisites steps of this node
     */
    public ArrayList<TreeNode> getPrerequisites(){
        return this.preqList;
    }

    public void addDependent(TreeNode t)
    {
    	this.depList.add(t);
    }
    
    /**
     *
      * @return the dependent steps of this node
     */
    public ArrayList<TreeNode> getDependents(){
        return this.depList;
    }
    
    public int getIntentionIdx(ArrayList<TreeNode> intentions)
    {
    	// Store the return value to avoid having to calculate this over and over
    	if (this.intention_num == -1)
    	{
    		TreeNode topLevelGoal = this;
    		while (topLevelGoal.parent != null)
    		{
    			topLevelGoal = topLevelGoal.parent;
    		}
    		
    		this.intention_num = intentions.indexOf(topLevelGoal);
    	}
    	
    	return this.intention_num;
    }
    
    /**
     * @return the next step of an action in the given intention
     */
    /*
    public TreeNode nextIstep()
    {
        // if it is not the last step in a plan
        if(next != null) {
            return next;
        }
        // if it is
        else{
            // if it is the top-level goal
            if(parent == null) {
                return null;   
            } else {
                // get the goal it is going to achieve
                GoalNode gn = (GoalNode)parent.getParent();
                return gn.nextIstep();
            }
        }
    }
    */
}
