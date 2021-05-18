package scheduler_GPG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.TreeMap;

import goalplantree.PlanNode;
import goalplantree.TreeNode;

public class Linearisation {

	final static boolean USE_DEFAULT_LINEARISATION = false; // true;
	
    static Random rm = new Random();
    
    public String name;
    public HashMap<String, TreeMap<Double, String>> plan_orders;
    public HashMap<String, Double> node_ranks;
    
    public Linearisation(String name)
    {
    	this.name = name;
    	this.plan_orders = new HashMap<String, TreeMap<Double, String>>();
    	this.node_ranks = new HashMap<String, Double>();
    }
    
    public TreeMap<Double, String> getPlanOrder(PlanNode plan)
    {
    	TreeMap<Double, String> plan_order;
    	
    	if (!plan_orders.containsKey(plan.getType()))
    	{
    		plan_order = new TreeMap<Double, String>();
    		
    		TreeNode[] p_body = plan.getPlanbody();
    		
    		double i = 0.0;
    		
    		for (TreeNode p_item : p_body)
    		{
    			double rank;
    			if (USE_DEFAULT_LINEARISATION)
    			{
    				rank = i;
    			}
    			else
    			{
    				rank = rm.nextDouble();
    			}
    			
    			plan_order.put(rank, p_item.getType());
    			node_ranks.put(p_item.getType(), rank);
    			
    			i++;
    		}
    		
    		plan_orders.put(plan.getType(), plan_order);
    	}
    	else
    	{
    		plan_order = plan_orders.get(plan.getType());
    	}
    	
    	return plan_order;
    }
    
    private ArrayList<Double> getNodeRankArray(TreeNode n)
    {
    	ArrayList<Double> result = new ArrayList<Double>();
    	
    	TreeNode currentNode = n;
    	while (currentNode.getParent() != null)
    	{
    		if (currentNode.getParent() instanceof PlanNode)
    		{
    			getPlanOrder((PlanNode)currentNode.getParent()); // Just to ensure that the ranks are established
    			result.add(0, node_ranks.get(currentNode.getType()));
    		}
    		
    		currentNode = currentNode.getParent();
    	}
    	
    	return result;
    }
    
    
    public int getNodePrecedence(TreeNode n1, TreeNode n2)
    {
    	ArrayList<Double> n1_rank_array = getNodeRankArray(n1);
    	ArrayList<Double> n2_rank_array = getNodeRankArray(n2);
    	
    	int ubound = Math.min(n1_rank_array.size(), n2_rank_array.size());
    	
    	for (int i = 0; i < ubound; i++)
    	{
    		if (n1_rank_array.get(i) < n2_rank_array.get(i))
    		{
    			return 1;
    		}
    		else if (n1_rank_array.get(i) > n2_rank_array.get(i))
    		{
    			return -1;
    		}
    	}
    	
    	// Note: This bit is probably unnecessary.
    	if (n1_rank_array.size() > n2_rank_array.size())
    	{
    		return 1;
    	}
    	else if (n1_rank_array.size() < n2_rank_array.size())
    	{
    		return -1;
    	}
    	
    	return 0;
    }
}
