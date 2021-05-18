package scheduler_GPG;

public abstract class Scheduler_GPG {
	
	public Match_GPG match;
	public int agent_num;
	public boolean mirror_match;
	
	public double[][] allianceMatrix;
	public boolean[][] available_intentions;
	public double[][] intention_values;
	
    public abstract Decision_GPG getDecision(State_GPG state);
    
    public abstract void reset();
    
    public void loadMatchDetails(Match_GPG match, int agent_num, boolean mirror_match)
    {
    	this.match = match;
    	this.agent_num = agent_num;
    	this.mirror_match = mirror_match;
    	
		this.allianceMatrix = new double[match.numAgents][match.numAgents];
		this.available_intentions = new boolean[match.schedulers.length][match.numGoalPlanTrees];
		this.intention_values = new double[match.schedulers.length][match.numGoalPlanTrees];
		
		for (int i = 0; i < match.numAgents; i++)
		{
			for (int j = 0; j < match.numAgents; j++)
			{
				if (i == j)
				{
					// An agent is always allied with itself
					allianceMatrix[i][j] = 1.0;
				}
				else
				{
					switch(match.allianceType)
					{
						case ADVERSARIAL:
							allianceMatrix[i][j] = -1.0;
							break;
							
						case ALLIED:
							allianceMatrix[i][j] = 1.0;
							break;
							
						case NEUTRAL:
						default:
							allianceMatrix[i][j] = 0.0;
					}
				}
			}
			
		}
		
		for (int intentionNum = 0; intentionNum < match.numGoalPlanTrees; intentionNum++)
		{
			int agentToAssignIntention = mirror_match? ((intentionNum + 1) % match.numAgents) : (intentionNum % match.numAgents);
			
			for (int agentNum = 0; agentNum < match.numAgents; agentNum++)
			{
				available_intentions[agentNum][intentionNum] = (agentNum == agentToAssignIntention);
			}
			
			for (int agentNum = 0; agentNum < match.numAgents; agentNum++)
			{
				intention_values[agentNum][intentionNum] = allianceMatrix[agentNum][agentToAssignIntention];
			}
		}
    }
}
