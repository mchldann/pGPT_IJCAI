/*
 * Copyright 2016 Yuan Yao
 * University of Nottingham
 * Email: yvy@cs.nott.ac.uk (yuanyao1990yy@icloud.com)
 *
 * Modified 2019 IPC Committee
 * Contact: https://www.intentionprogression.org/contact/
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details 
 *  <http://www.gnu.org/licenses/gpl-3.0.html>.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uno.gpt.generators;
import uno.gpt.structure.*;

import java.util.*;

/**
 * @version 2.0
 * @author yuanyao
 *
 * A new version of goal-plan tree generator
 */
public class SynthGenerator extends AbstractGenerator {
	/** Default values */
	static final int def_depth = 3,
						def_num_goal = 3,
						def_num_plan = 3,
						def_num_action = 3,
						def_num_var = 60,
						def_num_selected = 30,
						def_num_literal = 1;
	static final double def_safety_factor = 0.5d;
	static final double def_prob = 0.7d;

	/** id of the tree */
	private int id;

	/** total number of goals in this goal plan tree */
	private int treeGoalCount;

	/** total number of plans in this goal plan tree */
	private int treePlanCount;

	/** total number of actions in this goal plan tree */
	private int treeActionCount;

	/** random generators */
	final private Random rm;

	/** depth of the tree */
	final private int tree_depth;

	/** number of trees */
	final private int num_tree;

	/** number of goals */
	final private int num_goal;

	/** number of plans */
	final private int num_plan;

	/** number of actions */
	final private int num_action;

	/** number of environment variables */
	final private int num_var;

	/** number of literals **/
	final private int num_lit;

	/** probabilty of an effect selected as precondition */
	final private double prob;

	/**
	 * number of environment variables that can be used as post-condition of actions
	 **/
	final private int num_sel;

	/** the set of variables selected*/
	private ArrayList<Integer> selected_indexes;

	/** the set of irrelevant literals*/
	private ArrayList<Literal> is;

	/** Constructor
	 * add a new variable num_sel
	 */
	SynthGenerator(int seed, int tree_depth, int num_tree, int num_goal, int num_plan, int num_action, int num_var,
				   int num_sel, int num_lit, double prob)
	{
		this.rm = new Random(seed);
		this.tree_depth = tree_depth;
		this.num_tree = num_tree;
		this.num_goal = num_goal;
		this.num_plan = num_plan;
		this.num_action = num_action;
		this.num_var = num_var;
		this.num_sel = num_sel;
		this.num_lit = num_lit;
		this.prob = prob;
	}

	/**
	 * Generate environment
	 * @return the generated environment*/
	public HashMap<String, Literal> genEnvironment(){
		environment = new HashMap<>();
		Literal workingLit;

		// generate goal literals, all of which are false initially
		for (int i = 0; i < num_tree; i++) {
			workingLit = new Literal("G-" + i, false, false, false, 0);
			environment.put(workingLit.getId(), workingLit);
		}

		// generate all the  environment literals with their initial value
		for (int i = 0; i < num_var; i++) {

			boolean v = rm.nextBoolean();

//			double error = rm.nextGaussian()/5.16 + 0.5;
//			while (error < 0 || error > 1){
//			    error = rm.nextGaussian()/5.16 + 0.5;
//            }

			workingLit = new Literal("EV-" + i, v, true, false, v? 0.5:0.5);
			environment.put(workingLit.getId(), workingLit);
		}
		return environment;
	}


	/**
	 * A function for producing the top level goals for the GPTs
	 * @param index The index of the Goal being produced
	 * @return A Goal Node
	 */
	@Override
	public GoalNode genTopLevelGoal(int index) {
		// Set the generator id
		this.id = index;

		// Set the counters for this tree to 0
		this.treeGoalCount = 0;
		this.treePlanCount = 0;
		this.treeActionCount = 0;

		// Add the Goal Condition
		Literal gc = produceLiteral("G-" + id, true);

		// randomly select the conditions that can be the post-condition of action in this gpt
		ArrayList<Literal> selected = selectVar(this.num_sel);
		// get the action literals
		ArrayList<Literal> actL = new ArrayList<>(selected.subList(0,this.num_sel));
		for(int i = 0; i < this.num_sel; i++){
			Literal cu = actL.get(i).clone();
			cu.setState(!actL.get(i).getState());
			actL.add(cu);
		}
		// get the irrelevant literals
		this.is = new ArrayList<>(selected.subList(this.num_sel,selected.size()));
		// the goal-condition
		ArrayList<Literal> gcs = new ArrayList<>();

		gcs.add(new Literal("G-" + index, true, false, false, 0));
		// create the top-level goal
		GoalNode tpg = createGoal(0, actL, new ArrayList<>(), gcs);
		// return top-level goal
		return tpg;
	}



	/**
	 * select m literals that can be used as post-condition of actions in this gpt
	 * @return
	 */
	private ArrayList<Literal> selectVar(int m){
		// note that m must be less than or equal to num_var
		// randomly pick m different indexes
		this.selected_indexes = new ArrayList<>();

		while (selected_indexes.size() < m){
			int index = rm.nextInt(this.num_var);
			if(!selected_indexes.contains(index)){
				selected_indexes.add(index);
			}
		}

		// return the corresponding literal in the current environment
		ArrayList<Literal> result = new ArrayList<>();

		// the set of literals that are not selected
		ArrayList<Literal> irr = new ArrayList<>();


		for(int i = 0; i < this.num_var; i++){
			// if the index of this literal is selected
			if(this.selected_indexes.contains(i)){
				result.add(environment.get("EV-" + i));
			}
			// otherwise, this literal is categorised as irrelevant
			else {
				irr.add(environment.get("EV-" + i));
			}

		}
		result.addAll(irr);
		return  result;
	}

	/**
	 * a function to recursively create and construct a goal and all its hierarchies below
	 * @param depth current depth
	 * @param as the set of literals could be used as postcondition of actions in this tree
	 * @param ps the precondition of this goal
	 * @param gcs the goal-condition of this goal
	 * @return
	 */
	private GoalNode createGoal(int depth, ArrayList<Literal> as, ArrayList<Literal> ps, ArrayList<Literal> gcs){

		GoalNode goalNode = new GoalNode("T" + this.id + "-G" + this.treeGoalCount++);
		// incondition
		ArrayList<Literal> inc = new ArrayList<>();
		// plans to achieve this goal
		ArrayList<PlanNode> plans = new ArrayList<>();
		// clone the irrelevant literals, we assume the number of literals in potential is greater than or equals to
		// the number of plans need to be generated
		ArrayList<Literal> potential = (ArrayList<Literal>) is.clone();
		// create p plans
		for(int i = 0; i < this.num_plan; i++){
			// precondition
			ArrayList<Literal> prec = new ArrayList<>(ps);


			if(potential.size() > 0){

				if (prec.size() == 2){
					prec.remove(1);
				}

				//while (prec.size() < this.num_lit) {
					// randomly select a pure environmental condition
					int j = rm.nextInt(potential.size());
					// add it to the precondtion of this plan
					prec.add(potential.get(j));
					// remove it from the set of possible environmental literals
					potential.remove(j);
				//}
			}


			// create a plan
			PlanNode plan = createPlan(depth, as, prec, gcs);
			plans.add(plan);
			// for all plans to achieve goal g, set g as their prerequisite step
			plan.getPreqList().add(goalNode);
		}
		goalNode.getPlans().addAll(plans);
		goalNode.getGoalConds().addAll(gcs);




		// create the goal node
		return goalNode;
	}


	private PlanNode createPlan(int depth, ArrayList<Literal> as, ArrayList<Literal> prec, ArrayList<Literal> gcs){
		PlanNode planNode = new PlanNode("T" + this.id + "-P" + this.treePlanCount++);

		// plan body
		ArrayList<Node> planbody = new ArrayList<>();
		// the number of step in a plan
		int stepnum;
		// if it is a root plan then it only contains actions
		if(depth == this.tree_depth-1){
			stepnum = this.num_action;
		}
		// otherwise, it contains both actions and subgoals
		else{
			stepnum = this.num_action + this.num_goal;
		}


		// create the list of execution steps and the postcondition of this plan
		ArrayList<ActionNode> steps = new ArrayList<>();
		ArrayList<Literal> postc = createPlanBody(stepnum, prec, gcs, as, steps);
		// assign type for each step
		ArrayList<Boolean> types = assignPosition(stepnum);
		// calculate the same conditions
		ArrayList<Literal> safeC = safeCondition(steps, as);

		// create each action and subgoal
		for(int i = 0; i < types.size(); i++){
			// if it is an action
			if(types.get(i)){
				ActionNode actionNode = new ActionNode("T" + this.id + "-A" + this.treeActionCount++, steps.get(i).getPreC(),
						steps.get(i).getInC(), steps.get(i).getPostC());
				planbody.add(actionNode);
			}
			// if it is a subgoal
			else{
			    // remove the goal-condition of a subgoal from the plan's postcondition
                ArrayList<Literal> pc = steps.get(i).getPostC();

                for(int m = 0; m < pc.size(); m++){
                    for(int n = 0; n < postc.size(); n++){
                        if(postc.get(n).getId().equals(pc.get(m).getId()) &&
                        postc.get(n).getState() == pc.get(m).getState()){
                            postc.remove(n);
                            break;
                        }
                    }
                }

				GoalNode subgoal = createGoal(depth+1, safeC, steps.get(i).getPreC(), steps.get(i).getPostC());
				planbody.add(subgoal);
			}
		}



		// create the plan node
		planNode.getPlanBody().addAll(planbody);

		// set the dependencies of the first step
		planbody.get(0).getPreqList().add(planNode);
		planNode.getDepList().add(planbody.get(0));

		// check and set the dependencies for all other steps
		for(int m = 1; m < planbody.size(); m++){
			// get the mth step
			Node step = planbody.get(m);

			// the precondition of this step
			ArrayList<Literal> stepPrec = new ArrayList<>();
			// the postcondition of this step
			ArrayList<Literal> stepPost = new ArrayList<>();

			// if it is an action
			if(step instanceof ActionNode){
				ActionNode an = (ActionNode) step;
				// get its precondition
				stepPrec = an.getPreC();
				// get its postcondition
				stepPost = an.getPostC();
			}
			// if it is a subgoal
			else if (step instanceof GoalNode){
				// for any of the plan to achieve it, we check the dependencies
				GoalNode gn = (GoalNode) step;
				ArrayList<PlanNode> pls = gn.getPlans();
				ArrayList<Literal> prePlan = pls.get(0).getPre();
				stepPrec = prePlan;
				stepPost = gn.getGoalConds();
			}


			// check if its preceeding steps in this plan establishes its precondition
			// check steps one by one from back to front
			for(int n = m - 1; n >= 0; n--){

				// get the step we want to check
				Node target = planbody.get(n);
				//
				ArrayList<Literal> targetPost = new ArrayList<>();
				//
				ArrayList<Literal> targetPrec = new ArrayList<>();

				// if it is an action
				if(target instanceof ActionNode){
					// cast it to an action
					ActionNode an2 = (ActionNode) target;
					// get its postcondition
					targetPost = an2.getPostC();
					// get its precondition
					targetPrec = an2.getPreC();
				}
				// if it is a subgoal
				else if(target instanceof GoalNode){
					// cast it to a subgoal
					GoalNode gn = (GoalNode) target;
					// get its goal-condition
					targetPost = gn.getGoalConds();
					// get its precondition
					targetPrec = gn.getPlans().get(0).getPre();
				}


				// check if there is dependency link
				if(intersect(stepPrec, targetPost)){
					// if there is, then check if its dependent step is its prerequisite step already
					// e.g., a1 is the prerequisite step of a2 and a3, a2 is the prerequisite step of a3,
					// then a3 is dependent on the execution of a2 only
					if(!checkPrerequis(target, step)){
						// if not
						// the second action is the prerequisite step of the first one
						step.getPreqList().add(target);
						// the first action is the dependent step of the second one
						target.getDepList().add(step);
					}
				}

				// check if the precondition and postcondition conflicts
				if(conflicts(targetPrec, stepPost)){
					boolean isPrere = false;
					//for(int x = 0; x < preList.size(); x++){
					if(checkPrerequis(target, step)){
						isPrere = true;
						//break;
					}
					//}

					if(!isPrere){
						step.getPreqList().add(target);
						target.getDepList().add(step);

						if(step.getPreqList().contains(planNode))
							step.getPreqList().remove(planNode);
						planNode.getDepList().remove(step);
					}
				}


				// check if the precondition and postcondition conflicts
				if(conflicts(targetPost, stepPrec)){

					ArrayList<Node> pList = step.getPreqList();

					for(int x = 0; x < pList.size(); x++){
						Node node = pList.get(x);
						ArrayList<Literal> nPostc = new ArrayList<>();
						if(node instanceof ActionNode){
							ActionNode an3 = (ActionNode) node;
							nPostc = an3.getPostC();
						}
						if(node instanceof GoalNode){
							GoalNode gl3 = (GoalNode) node;
							nPostc = gl3.getGoalConds();
						}
						if(conflicts(nPostc, targetPost)){
							if(!checkPrerequis(target, node)){
								node.getPreqList().add(target);
								target.getDepList().add(node);
								if(node.getPreqList().contains(planNode))
									node.getPreqList().remove(planNode);
								planNode.getDepList().remove(node);
							}
						}
					}
				}


			}


			// if this step does not dependent on any other steps
			if(step.getPreqList().size() == 0){
				// add the plan itself as its prerequisite
				step.getPreqList().add(planNode);
				planNode.getDepList().add(step);
			}

		}


		for(int i = 0; i < planbody.size(); i++){
			System.out.println(planbody.get(i).getName() + ": ");
			System.out.print("prerequisite: ");
			for(int j = 0; j < planbody.get(i).getPreqList().size(); j++){
				System.out.print(planbody.get(i).getPreqList().get(j).getName()+";");
			}
			System.out.println();
			System.out.print("dependent:");
			for(int j = 0; j < planbody.get(i).getDepList().size(); j++){
				System.out.print(planbody.get(i).getDepList().get(j).getName()+";");
			}
			System.out.println();
		}



		planNode.getPre().addAll(prec);


		// remove the postcondition

		for(int m = 0; m < prec.size(); m++){
			for(int n = 0; n < postc.size(); n++){
				if(postc.get(n).getId().equals(prec.get(m).getId()) &&
				postc.get(n).getState() == prec.get(m).getState()){
					postc.remove(n);
					break;
				}
			}
		}




		planNode.getPost().addAll(postc);

		return planNode;

	}

	private boolean checkPrerequis(Node target, Node base){
		// get the list of prerequisite steps
		ArrayList<Node> preList = base.getPreqList();

		for(int i = 0; i < preList.size(); i++){
			// if the target is already its prerequisite step
			if(preList.get(i) == target) {
				return true;
			}
			// check the prerequisite of these steps
			else {
				if(checkPrerequis(target, preList.get(i))){
					return true;
				}
			}
		}

		return false;
	}


	private ArrayList<Literal> createPlanBody(int stepNum, ArrayList<Literal> prec, ArrayList<Literal> gcs, ArrayList<Literal> as, ArrayList<ActionNode> steps){
		// current states, copied from the precondition of the plan
		ArrayList<Literal> current = new ArrayList<>();
		for(int i = 0; i < prec.size(); i++){
			current.add(prec.get(i));
		}
		// possible action literals copied from as, we also ensure that there is no action make the current state true
		ArrayList<Literal> actionLiteral = new ArrayList<>(as);
		// a list of leaf action nodes which do not have any other nodes depend on them
		ArrayList<ActionNode> lAction = new ArrayList<>();


		for(int i = 0; i < stepNum; i++){

			// the list of prerequisite
			ArrayList<Node> prereq = new ArrayList<>();

			// the precondition of the action
			ArrayList<Literal> precondition = new ArrayList<>();
			// the precondition of the first step is the same as the precondition of this plan
			if(i == 0){
				precondition = prec;
			}
			// if it is the last step in this plan, then its precondition contains all conditions established by other
			// steps but have not been required yet
//			else if( i == stepNum -1){
//				// check all the leaf actions
//				for(int j = 0; j < lAction.size(); j++){
//					// get their postconditions
//					ArrayList<Literal> ps = lAction.get(j).getPostC();
//					// add them to the precondition
//					for(int x = 0; x < ps.size(); x++){
//						if(!contains(precondition, ps.get(x))){
//							precondition.add(ps.get(x));
//						}
//					}
//				}
//			}
			// if it is not then randomly select num_lit conditions from the current state.
			else {
				// decide the number of literals in action's precondition
				int literalNum;
				if(this.num_lit > current.size())
					literalNum = current.size();
				else
					literalNum = this.num_lit;

				// randomly select from the set of current state
				while (precondition.size() < literalNum){
					// select a literal
					int sx = rm.nextInt(current.size());
					// ignore the precondition that already appears
					while(precondition.contains(current.get(sx))){
						sx = rm.nextInt(current.size());
					}
					// add it to the set of preconditions
					precondition.add(current.get(sx));
				}
				//precondition.add(randomPeffect(this.prob, current));

			}

			ArrayList<Literal> postcondition = new ArrayList<>();
			// if this step is the last action, then it has the goal-condition as its postcondition
			if(i == stepNum - 1){
				postcondition = gcs;
			}
			// the postcondition is randomly generated apart from the last action
			else{
				// generate multiple postcondition
				// while(postcondition.size() < this.num_lit){
					// randomly select a postcondition
					int index = rm.nextInt(actionLiteral.size());
					Literal p = actionLiteral.get(index);
					postcondition.add(p);
					// update the current state
					updateCurrentLiterals(current, p);
					// update the set of action
					updateActionLiterals(actionLiteral, p);
				//}
			}


			ActionNode action = new ActionNode("", precondition, new ArrayList<>(), postcondition);

			steps.add(action);
		}


		// add the goal-condition
		for(int i = 0; i < gcs.size(); i++){
			current.add(gcs.get(i));
		}

		return current;
	}

	/**
	 * checks whether a literal l is in ls
	 * @param ls
	 * @param l
	 * @return
	 */
	private boolean contains(ArrayList<Literal> ls, Literal l){
		for(int i = 0; i < ls.size(); i++){
			if(ls.get(i).getId().equals(l.getId()) && ls.get(i).getState() == l.getState())
				return true;
		}
		return false;
	}




	private ArrayList<Node> findPrereq(ArrayList<ActionNode> ans, ArrayList<Literal> prec, ArrayList<Literal> postc){

		// initialise the list of prerequisite steps
		ArrayList<Node> prereqs = new ArrayList<>();

		for(int i = 0; i < ans.size(); i++){
			ArrayList<Node> preq = findPrereq(ans.get(i), prec, postc);
			prereqs.addAll(preq);
		}

		return prereqs;
	}


	private ArrayList<Node> findPrereq(ActionNode an, ArrayList<Literal> prec, ArrayList<Literal> pc){

		ArrayList<Node> prereqs = new ArrayList<>();
		// get the postcondition of the given action
		ArrayList<Literal> postc = an.getPostC();
		// there is a dependency
		if(intersect(postc, prec) || conflicts(postc, pc)){
			prereqs.add(an);
		}
		// if there is not
		else{
			// get its prereqs
			ArrayList<Node> ps = an.getPreqList();
			// for each step find prerequisite
			for(int i = 0; i < ps.size(); i++){
				ActionNode actionNode = (ActionNode) ps.get(i);
				// get its preq relation
				ArrayList<Node> nodes = findPrereq(actionNode, prec, pc);
				prereqs.addAll(nodes);
			}
		}
		return prereqs;
	}


	/**
	 * @param ls1 the first set of literals
	 * @param ls2 the second set of literals
	 * @return true, if they contain same elements; false, otherwise
	 */
	private boolean intersect(ArrayList<Literal> ls1, ArrayList<Literal> ls2){

		if(ls1.size() * ls2.size() != 0){

			for(int i = 0; i < ls1.size(); i++){
				Literal l = ls1.get(i);
				for(int j = 0; j < ls2.size() ; j++){
					if(l.equals(ls2.get(j))){
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * @param ls1
	 * @param ls2
	 * @return true, if they contain conflicting literals; false, otherwise
	 */
	private boolean conflicts(ArrayList<Literal> ls1, ArrayList<Literal> ls2){

		if(ls1.size() * ls2.size() != 0){
			for(int i = 0; i < ls1.size(); i++){
				Literal l = ls1.get(i);
				for(int j = 0; j < ls2.size() ; j++){
					if(l.getId().equals(ls2.get(j).getId()) && (l.getState() != ls2.get(j).getState())){
						return true;
					}
				}
			}
		}
		return false;
	}


	/**
	 * update the current state based on a literal l. If a literal l(its negation) is in ls, then remove it.
	 * Add l in the tail of this list
	 * @param ls
	 * @param l
	 */
	private void updateCurrentLiterals(ArrayList<Literal> ls, Literal l){
		for(int i = 0; i < ls.size(); i++){
			if(ls.get(i).getId().equals(l.getId())){
				ls.remove(i);
				break;
			}
		}
		ls.add(l);
	}

	/**
	 * update the list of action literals. If a literal l is achieved, then remove l from ls and add its negation to ls
	 * @param ls
	 * @param l
	 */
	private void updateActionLiterals(ArrayList<Literal> ls, Literal l){
		int index = -1;
		for(int i = 0; i < ls.size(); i++){
			if(ls.get(i).getId().equals(l.getId())){
				// if they are exactly the same
				if(ls.get(i).getState() == l.getState()){
					// if its negation has not been found yet
					if(index == -1){
						index = i;
					}
					ls.remove(i);
					break;
				}
				// if its negation was found
				else{
					index = - 2;
				}
			}
		}

		if(index != -2){
			for(int i = index; i < ls.size(); i++){
				if(ls.get(i).getState() == l.getState()){
					index = -2;
					break;
				}
			}
		}

		if(index != -2) {
			ls.add(new Literal(l.getId(), !l.getState(), l.isStochastic(), l.isRandomInit(), l.getProbability()));
		}
	}


	/**
	 * assign the typs for each execution steps
	 * @param stepNum
	 * @return
	 */
	private ArrayList<Boolean> assignPosition(int stepNum){
		ArrayList<Boolean> positions = new ArrayList<>();
		for(int i = 0; i < stepNum; i++){
			positions.add(true);
		}

		if(stepNum != this.num_action){
			ArrayList<Integer> goal_pos = new ArrayList<>();
			while (goal_pos.size() < this.num_goal){
				int index = rm.nextInt(stepNum-2);
				if(!goal_pos.contains(index+1)){
					goal_pos.add(index+1);
					positions.set(index+1,false);
				}
			}
		}

		return positions;
	}

	private ArrayList<Literal> safeCondition(ArrayList<ActionNode> steps, ArrayList<Literal> conds){
		// clone the current action literals
		ArrayList<Literal> actionLiteral = (ArrayList<Literal>) conds.clone();

		// remove all conflicting conditions
		removeConflicting(actionLiteral, steps.get(0).getPreC());
		for(int i = 0; i < steps.size(); i++){
			removeConflicting(actionLiteral, steps.get(i).getPostC());
		}

		return actionLiteral;
	}

	/**
	 * remove all the conflicting literals
	 * @param ls
	 * @param l
	 */
	private void removeConflicting(ArrayList<Literal> ls, ArrayList<Literal> l){
		int index = ls.size();
		for(int j = 0; j < l.size(); j++){
			for(int i = 0; i < ls.size(); i++){
				if(ls.get(i).getId().equals(l.get(j).getId())) {
					ls.remove(i);
					index = i;
				}
			}

			for(int i = index; i < ls.size(); i++){
				if(ls.get(i).getId().equals(l.get(j).getId())) {
					ls.remove(i);
					break;
				}
			}
		}

	}


	/** Get the environmental or non-goal variables as ids */
	private List<String> getEnvLitsAsStrings(){
		// Make a new List from the literals of the environment
		List<String> envLits = new ArrayList<>(environment.keySet());
		// Filter out the goal Literals
		envLits.removeIf(l -> (l.startsWith("G-")));
		// Return this sublist
		return envLits;
	}


	/** Default wrapper */
	private List<Literal> makeSafe(){
		// Call full with defaults
		return makeSafe(new ArrayList<>(), num_plan);
	}

	/** Makes a given list of literals into a safe one by adding p ¬p pairs. */
	private List<Literal> makeSafe(List<Literal> list, int target){
		// Get the IDs for the literals we are working with.
		List<String> envLits = getEnvLitsAsStrings();
		// Loop over the given list and remove those from the pool.
		for (Literal lit: list) {
			envLits.removeIf(s -> lit.getId().equals(s));
		}
		// If you can add a pair
		while(list.size() < target) {
			// Select an ID, remove it from the pool, get the corresponding literal and make a copy.
			Literal newLit = environment.get(envLits.remove(rm.nextInt(envLits.size()))).clone();
			// Add a copy of the clone, (One value)
			list.add(newLit.clone());
			// Flip the copy you haven't added
			newLit.flip();
			// Add that copy as well to have added both values of the literal
			list.add(newLit);
			//Ensure the environment isn't depleted
			if (envLits.size() == 0) {
				// Replenish it
				envLits = getEnvLitsAsStrings();
			}
		}
		// Return the list
		return list;
	}
}
