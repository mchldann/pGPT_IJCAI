package xml2bdi;
import beliefbase.BeliefBase;
import beliefbase.Condition;
import goalplantree.ActionNode;
import goalplantree.GoalNode;
import goalplantree.PlanNode;
import goalplantree.TreeNode;
import org.jdom2.*;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class XMLReader {

    private BeliefBase beliefs;
    private ArrayList<TreeNode> intentions;
    // a mapping from the name of the node to the node itself
    private HashMap<String, TreeNode> nodeLib = new HashMap<>();
    private HashMap<String, String> preqMap = new HashMap<>();
    private HashMap<String, String> depMap = new HashMap<>();
    private HashMap<String, String> parentMap = new HashMap<>();
    
    public XMLReader(String url) throws Exception{
        translate(url);
    }

    private void translate(String url) throws Exception{

        SAXBuilder builder = new SAXBuilder();
        Document read_doc = builder.build(new File(url));
        Element root = read_doc.getRootElement(); // get the root node of the xml file

        // get the goals, plans and actions
        List<Element> toplevelgoals = root.getChildren();
        // get the environment
        Element environment = toplevelgoals.get(0);
        List<Element> literals = environment.getChildren();
        beliefs = new BeliefBase("default");
        for(int i = 0; i < literals.size(); i++){
            beliefs.apply(new Condition(literals.get(i).getAttributeValue("name"),
                    literals.get(i).getAttributeValue("initVal").equals("true")));
        }

        intentions = new ArrayList<>();
        // generate goal-plan tree for each intention
        for( int i = 1; i < toplevelgoals.size(); i++){
            // clear the hash map
            //nodeLib.clear(); // Michael note: These clear() lines meant that only the maps for the most recently read intention were persisting!
            //preqMap.clear();
            //depMap.clear();
            // generate nodes
            GoalNode goal = readGoal(toplevelgoals.get(i), null);
            intentions.add(goal);
            // go through the whole gpt to get the prerequisite info
            depTraversal(goal);
        }
    }
    
    /**
     * translate an xml element to a goal node
     * @param element the input xml element
     * @return the goal node
     */
    private GoalNode readGoal(Element element, String parent){
        // get the name of this goal
        String name = element.getAttributeValue("name");
        // read and translate the goal-condition of this goal
        Condition[] conditions = readCondition(element.getAttributeValue("goal-condition"));
        // get the child element
        List<Element> children = element.getChildren();
        // get the number of plan node
        PlanNode[] plans = new PlanNode[children.size()];
        // read and construct the plan node
        for(int i = 0; i < children.size(); i++){
            plans[i] = readPlan(children.get(i), name);
        }
        // generate a new goal node
        GoalNode gn = new GoalNode("", name, new Condition[0], plans, conditions);

        // get the prerequisites steps
        String prerequisites = element.getAttributeValue("prerequisite");
        // get the dependent steps
        String dependents = element.getAttributeValue("dependent");
        // add this goal to the map
        nodeLib.put(name, gn);
        // add prerequisite info
        preqMap.put(name, prerequisites);
        // add dependent info
        depMap.put(name, dependents);
        // add parent info
        parentMap.put(name, parent);

        return gn;
    }

    /**
     * translate an xml element to a plan node
     * @param element
     * @return
     */
    private PlanNode readPlan(Element element, String parent){
        // read the name of this plan
        String name = element.getAttributeValue("name");
        // read and translate the precondition of this plan
        Condition[] conditions = readCondition(element.getAttributeValue("precondition"));
        // read and translate the postcondition of this action
        Condition[] postc = readCondition(element.getAttributeValue("postcondition"));
        // read and construct the execution steps in this plan
        List<Element> elements = element.getChildren();
        TreeNode[] steps = new TreeNode[elements.size()];
        for(int i = 0; i < steps.length; i++){
            // get the type of this step
            String type = elements.get(i).getName();
            if(type.equals("Action")){
                ActionNode actionNode = readAction(elements.get(i), name);
                steps[i] = actionNode;
            }
            else if(type.equals("Goal")){
                GoalNode goalNode = readGoal(elements.get(i), name);
                steps[i] = goalNode;
            }
        }

        PlanNode planNode = new PlanNode("", name, conditions, new Condition[0], postc, steps);

        // get the prerequisites steps
        String prerequisites = element.getAttributeValue("prerequisite");
        // get the dependent steps
        String dependents = element.getAttributeValue("dependent");
        // add this goal to the map
        nodeLib.put(name, planNode);
        // add prerequisite info
        preqMap.put(name, prerequisites);
        // add dependent info
        depMap.put(name, dependents);
        // add parent info
        parentMap.put(name, parent);
        
        return planNode;
    }
    
    /**
     * translate an xml element to an action node
     * @param element
     * @return
     */
    private ActionNode readAction(Element element, String parent){
        // read the name of this action
        String name = element.getAttributeValue("name");
        // read and translate the precondition of this action
        Condition[] prec = readCondition(element.getAttributeValue("precondition"));
        // read and translate the postcondition of this action
        Condition[] postc = readCondition(element.getAttributeValue("postcondition"));
        // generate a new action node
        ActionNode actionNode = new ActionNode("", name, prec, new Condition[0], postc);


        // get the prerequisites steps
        String prerequisites = element.getAttributeValue("prerequisite");
        // get the dependent steps
        String dependents = element.getAttributeValue("dependent");
        // add this goal to the map
        nodeLib.put(name, actionNode);
        // add prerequisite info
        preqMap.put(name, prerequisites);
        // add dependent info
        depMap.put(name, dependents);
        // add parent info
        parentMap.put(name, parent);
        
        return actionNode;
    }


    /**
     * read a string and generate corresponding conditions
     * @param conditions a list of string representing conditions
     * @return a list of conditions
     */
    private Condition[] readCondition(String conditions){
        conditions = conditions.replaceAll("\\)", "");
        conditions = conditions.replaceAll("\\(", "");
        conditions = conditions.replaceAll(";", "");
        conditions = conditions.replaceAll(" ", ""); // The original code didn't account for spaces!
        String[] literals = conditions.split(",");
        Condition[] cons = new Condition[literals.length / 2];
        for(int i = 0; i < cons.length; i++){
            cons[i] = new Condition(literals[i*2], literals[i*2 + 1].equals("true"));
        }
        return cons;
    }


    /**
     * get the belief set read from an xml file
     * @return
     */
    public BeliefBase getBeliefs(){
        return this.beliefs;
    }

    /**
     * get the intentions from the xml file
     * @return
     */
    public ArrayList<TreeNode> getIntentions(){
        return this.intentions;
    }

    /**
     * get the mapping from the name of the node to the node itself
     * @return
     */
    public HashMap<String, TreeNode> getNodeLib(){
        return this.nodeLib;
    }
    
    /**
     * get the preconditions map from the xml file
     * @return
     */
    public HashMap<String, String> getPreqMap(){
        return this.preqMap;
    }
    
    /**
     * get the dependencies map from the xml file
     * @return
     */
    public HashMap<String, String> getDepMap(){
        return this.depMap;
    }
    
    /**
     * get the parent map from the xml file
     * @return
     */
    public HashMap<String, String> getParentMap(){
        return this.parentMap;
    }
    
    public void depCalculation(TreeNode treeNode){

        // get the name of this goal
        String name = treeNode.getType();
        
        // get its prerequisite steps
        String preqString = preqMap.get(name);

        String[] preqList;
        if(preqString != null){
            preqList = preqString.split(";");
        }else {
            preqList = new String[0];
        }


        // get the prerequisite steps of this goal
        ArrayList<TreeNode> prerequisite = treeNode.getPrerequisites();
        prerequisite.clear();
        // add each step in the set of prerequisite steps to the goal
        for(int i = 0; i < preqList.length; i++){
            // get the node
            TreeNode step = nodeLib.get(preqList[i]);
            prerequisite.add(step);
        }

        // get its dependent steps
        String depString = depMap.get(name);
        String[] depList;
        if(depString != null){
            depList = depString.split(";");
        }else {
            depList = new String[0];
        }

        // add each step in the set of dependent steps to the goal
        for(int i = 0; i < depList.length; i++){
            // get the node
            TreeNode step = nodeLib.get(depList[i]);
            treeNode.addDependent(step);
        }
    }

    public void depTraversal(GoalNode goalNode){
        depCalculation(goalNode);
        // get plans
        PlanNode[] pls = goalNode.getPlans();
        // for each of its plans
        for(int i = 0; i < pls.length; i++){
            depTraversal(pls[i]);
        }
    }

    public void depTraversal(PlanNode planNode){
        depCalculation(planNode);
        // get its planbody
        TreeNode[] body = planNode.getPlanbody();
        for(int i = 0; i < body.length; i++){
            if(body[i] instanceof GoalNode){
                GoalNode gn = (GoalNode) body[i];
                depTraversal(gn);
            }
            else {
                depCalculation(body[i]);
            }
        }
    }

}
