import scheduler_GPG.MCTS_Scheduler_GPG;
import scheduler_GPG.Match_GPG;
import scheduler_GPG.Match_Info;
import scheduler_GPG.Random_Scheduler_GPG;
import scheduler_GPG.Scheduler_GPG;
import scheduler_GPG.State_GPG;
import xml2bdi.XMLReader;
import uno.gpt.generators.*;
import util.Log;

import java.util.ArrayList;
import java.util.Random;

import enums.Enums.AllianceType;
import enums.Enums.VisionType;

public class Main
{
    public static void main(String[] args) throws Exception
    {
    	boolean run_test_experiment = false;
    	boolean run_solo_experiment = false;
    	boolean run_main_experiment = true;
    	
    	Log.log_to_file = false;
    	
    	// NOTE: If xml_file == null, a random forest will be generated
    	String xml_file = null;
    	String generatedXmlFilename = null;
    	
    	int experiment_repetitions = 1000;
    	
    	// MCTS settings
        int mcts_alpha = 100;
        int mcts_beta = 10;
        double mcts_c = 2.0 * Math.sqrt(2.0); // Using a largeish value because the overall match scores are relatively large (not in [0, 1])
        
        // Assumed politeness controls how the MCTS agents expect the other agents in the environment to behave.
        // 0 --> Other agents are expected to behave as neutrals (selfishly).
        // 1 --> Other agents are expected to behave as allies.
        // -1 --> Other agents are expected to behave as adversaries.
		double[] assumed_politeness_of_other_agent = {0.0};

        // GPG Schedulers
		ArrayList<String> gpg_scheduler_names = new ArrayList<String>();
		ArrayList<Scheduler_GPG> gpg_schedulers = new ArrayList<Scheduler_GPG>();
		ArrayList<Scheduler_GPG> gpg_scheduler_clones = new ArrayList<Scheduler_GPG>(); // For mirror matches
		
		gpg_scheduler_names.add("Random");
		gpg_schedulers.add(new Random_Scheduler_GPG(false, true));
		gpg_scheduler_clones.add(new Random_Scheduler_GPG(false, true));
		
		gpg_scheduler_names.add("Random_GPT");
		gpg_schedulers.add(new Random_Scheduler_GPG(true, true));
		gpg_scheduler_clones.add(new Random_Scheduler_GPG(true, true));
		
		// *** pGPG VERSIONS ***
		gpg_scheduler_names.add("MCTS_fully_aware");
		gpg_schedulers.add(new MCTS_Scheduler_GPG(VisionType.FULL, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, false)); // Assumed politeness is reset in the experiments anyway
		gpg_scheduler_clones.add(new MCTS_Scheduler_GPG(VisionType.FULL, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, false));
		
		// Partial vision
		gpg_scheduler_names.add("MCTS_partially_aware");
		gpg_schedulers.add(new MCTS_Scheduler_GPG(VisionType.PARTIALLY_AWARE, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, false));
		gpg_scheduler_clones.add(new MCTS_Scheduler_GPG(VisionType.PARTIALLY_AWARE, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, false));

		gpg_scheduler_names.add("MCTS_unaware");
		gpg_schedulers.add(new MCTS_Scheduler_GPG(VisionType.UNAWARE, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, false));
		gpg_scheduler_clones.add(new MCTS_Scheduler_GPG(VisionType.UNAWARE, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, false));

		// *** LINEARISED VERSIONS ***
		gpg_scheduler_names.add("MCTS_fully_aware_GPT");
		gpg_schedulers.add(new MCTS_Scheduler_GPG(VisionType.FULL, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, true)); // Assumed politeness is reset in the experiments anyway
		gpg_scheduler_clones.add(new MCTS_Scheduler_GPG(VisionType.FULL, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, true));

		// Partial vision
		gpg_scheduler_names.add("MCTS_partially_aware_GPT");
		gpg_schedulers.add(new MCTS_Scheduler_GPG(VisionType.PARTIALLY_AWARE, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, true));
		gpg_scheduler_clones.add(new MCTS_Scheduler_GPG(VisionType.PARTIALLY_AWARE, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, true));

		gpg_scheduler_names.add("MCTS_unaware_GPT");
		gpg_schedulers.add(new MCTS_Scheduler_GPG(VisionType.UNAWARE, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, true));
		gpg_scheduler_clones.add(new MCTS_Scheduler_GPG(VisionType.UNAWARE, mcts_alpha, mcts_beta, mcts_c, 1.0, 1.0, true));
		
    	// NOTE: The below variables only apply if useRandomXML == true
    	int depth, numEnvironmentVariables, numVarToUseAsPostCond, numGoalPlanTrees, subgoalsPerPlan, plansPerGoal, actionsPerPlan;
    	double propGuaranteedCPrecond;
    	
    	// Generator settings
		depth = 5;
    	numEnvironmentVariables = 80;
    	numVarToUseAsPostCond = 40;
    	numGoalPlanTrees = 12;
    	subgoalsPerPlan = 1;
    	plansPerGoal = 2;
    	actionsPerPlan = 3;
    	propGuaranteedCPrecond = 0.5;

    	XMLReader reader;
        Random rm = new Random();
    	
        for (int experiment_num = 0; experiment_num < experiment_repetitions; experiment_num++)
        {
	    	String generatorArgsStr = "";
	    	
        	String generated_filename = "random_" + experiment_num + ".xml";
        	
	    	if (xml_file == null)
	    	{
		    	int randomSeed = rm.nextInt();
		    	
		        generatedXmlFilename = Log.getLogDir() + "/" + generated_filename;
		    	
		    	String[] generatorArgs = new String[21];
		    	generatorArgs[0] = "synth"; // Options are "synth", "miconic", "block", "logi"
		    	generatorArgs[1] = "-f";
		    	generatorArgs[2] = generatedXmlFilename;
		    	generatorArgs[3] = "-s";
		    	generatorArgs[4] = Integer.toString(randomSeed);
		    	generatorArgs[5] = "-d";
		    	generatorArgs[6] = Integer.toString(depth);
		    	generatorArgs[7] = "-t";
		    	generatorArgs[8] = Integer.toString(numGoalPlanTrees);
		    	generatorArgs[9] = "-v";
		    	generatorArgs[10] = Integer.toString(numEnvironmentVariables);
		    	generatorArgs[11] = "-g";
		    	generatorArgs[12] = Integer.toString(subgoalsPerPlan);
		    	generatorArgs[13] = "-p";
		    	generatorArgs[14] = Integer.toString(plansPerGoal);
		    	generatorArgs[15] = "-a";
		    	generatorArgs[16] = Integer.toString(actionsPerPlan);
		    	generatorArgs[17] = "-y";
		    	generatorArgs[18] = Double.toString(propGuaranteedCPrecond);
		    	generatorArgs[19] = "-w";
		    	generatorArgs[20] = Integer.toString(numVarToUseAsPostCond);
		    	
		    	for (int argNum = 0; argNum < generatorArgs.length; argNum++)
		    	{
		    		generatorArgsStr = generatorArgsStr + generatorArgs[argNum] + " ";
		    	}
		    	Log.info(generatorArgsStr);

		    	XMLGenerator.generate(generatorArgs);
		    	
		    	reader = new XMLReader(generatedXmlFilename);
	    	}
	    	else
	    	{
	            reader = new XMLReader(xml_file);
	    	}
	    	
	    	String forest_name = (xml_file == null)? generated_filename : xml_file;
	    	
	        // Read the initial state from the XML file
	    	State_GPG currentStateGPG = new State_GPG(forest_name, reader.getBeliefs(), reader.getIntentions(), reader.getNodeLib(), reader.getPreqMap(), reader.getParentMap(), 0);
	    	
	    	currentStateGPG.reset_linearisations();
	        
	        if (run_test_experiment)
	        {
        		new Match_GPG("neutral_" + gpg_scheduler_names.get(3) + "_and_" + gpg_scheduler_names.get(6) + "_and_" + gpg_scheduler_names.get(6),
		            	numGoalPlanTrees,
		            	AllianceType.NEUTRAL,
		            	currentStateGPG.clone(),
		            	new Scheduler_GPG[] {gpg_schedulers.get(3), gpg_schedulers.get(6), gpg_scheduler_clones.get(6)},
		            	new String[] {gpg_scheduler_names.get(3), gpg_scheduler_names.get(6), gpg_scheduler_names.get(6) + "_clone"},
		            	0).run(true, true, false);
	        }
	    	
	        if (run_solo_experiment)
	        {
	    		Match_Info m_info = new Match_GPG("solo_" + gpg_scheduler_names.get(3),
		            	numGoalPlanTrees,
		            	AllianceType.ALLIED,
		            	currentStateGPG.clone(),
		            	new Scheduler_GPG[] {gpg_schedulers.get(3)},
		            	new String[] {gpg_scheduler_names.get(3)},
		            	1.0).run(true,  true,  false);
	        }
	        
	        if (run_main_experiment)
	        {
	        	for (int i = 0; i < assumed_politeness_of_other_agent.length; i++)
		        {
	        		double assumed_politeness = assumed_politeness_of_other_agent[i];
	        		
		        	// Reset assumed politeness
			        for (Scheduler_GPG sched : gpg_schedulers)
			        {
			        	if (sched instanceof MCTS_Scheduler_GPG)
			        	{
			        		((MCTS_Scheduler_GPG)sched).assumed_politeness_of_other_agents = assumed_politeness;
			        	}
			        }
			        for (Scheduler_GPG sched : gpg_scheduler_clones)
			        {
			        	if (sched instanceof MCTS_Scheduler_GPG)
			        	{
			        		((MCTS_Scheduler_GPG)sched).assumed_politeness_of_other_agents = assumed_politeness;
			        	}
			        }
			        
			        // Allied experiments
			        for (int agent_1 = 0; agent_1 < gpg_schedulers.size(); agent_1++)
			        {
			        	for (int agent_2 = agent_1; agent_2 < gpg_schedulers.size(); agent_2++)
			        	{
			        		// For assumed_politeness == 1, only rerun experiments for I_A schedulers (since assumed politeness is irrelevant to the others)
			        		if ((assumed_politeness == 0)
			        				|| gpg_scheduler_names.get(agent_1).equals("MCTS_fully_aware")
			        				|| gpg_scheduler_names.get(agent_1).equals("MCTS_partially_aware")
			        				|| gpg_scheduler_names.get(agent_2).equals("MCTS_fully_aware")
			        				|| gpg_scheduler_names.get(agent_2).equals("MCTS_partially_aware"))
			        		{
					        	if (agent_2 == agent_1)
					        	{
					        		// Mirror match
							        new Match_GPG("allied_" + gpg_scheduler_names.get(agent_1) + "_and_" + gpg_scheduler_names.get(agent_2),
							            	numGoalPlanTrees,
							            	AllianceType.ALLIED,
							            	currentStateGPG.clone(),
							            	new Scheduler_GPG[] {gpg_schedulers.get(agent_1), gpg_scheduler_clones.get(agent_2)},
							            	new String[] {gpg_scheduler_names.get(agent_1), gpg_scheduler_names.get(agent_2) + "_clone"},
							            	assumed_politeness).run_two_sided_series(true, true);
					        	}
					        	else
					        	{
							        new Match_GPG("allied_" + gpg_scheduler_names.get(agent_1) + "_and_" + gpg_scheduler_names.get(agent_2),
							            	numGoalPlanTrees,
							            	AllianceType.ALLIED,
							            	currentStateGPG.clone(),
							            	new Scheduler_GPG[] {gpg_schedulers.get(agent_1), gpg_schedulers.get(agent_2)},
							            	new String[] {gpg_scheduler_names.get(agent_1), gpg_scheduler_names.get(agent_2)},
							            	assumed_politeness).run_two_sided_series(true, true);
					        	}
			        		}
			        	}
			        }
			        
			        // Neutral experiments
			        for (int agent_1 = 0; agent_1 < gpg_schedulers.size(); agent_1++)
			        {
			        	for (int agent_2 = agent_1; agent_2 < gpg_schedulers.size(); agent_2++)
			        	{
			        		// For assumed_politeness == 1, only rerun experiments for I_A schedulers (since assumed politeness is irrelevant to the others)
			        		if ((assumed_politeness == 0)
			        				|| gpg_scheduler_names.get(agent_1).equals("MCTS_fully_aware")
			        				|| gpg_scheduler_names.get(agent_1).equals("MCTS_partially_aware")
			        				|| gpg_scheduler_names.get(agent_2).equals("MCTS_fully_aware")
			        				|| gpg_scheduler_names.get(agent_2).equals("MCTS_partially_aware"))
			        		{
					        	if (agent_2 == agent_1)
					        	{
					        		// Mirror match
					        		new Match_GPG("neutral_" + gpg_scheduler_names.get(agent_1) + "_and_" + gpg_scheduler_names.get(agent_2),
							            	numGoalPlanTrees,
							            	AllianceType.NEUTRAL,
							            	currentStateGPG.clone(),
							            	new Scheduler_GPG[] {gpg_schedulers.get(agent_1), gpg_scheduler_clones.get(agent_2)},
							            	new String[] {gpg_scheduler_names.get(agent_1), gpg_scheduler_names.get(agent_2) + "_clone"},
							            	assumed_politeness).run_two_sided_series(true, true);
					        	}
					        	else
					        	{
					        		new Match_GPG("neutral_" + gpg_scheduler_names.get(agent_1) + "_and_" + gpg_scheduler_names.get(agent_2),
							            	numGoalPlanTrees,
							            	AllianceType.NEUTRAL,
							            	currentStateGPG.clone(),
							            	new Scheduler_GPG[] {gpg_schedulers.get(agent_1), gpg_schedulers.get(agent_2)},
							            	new String[] {gpg_scheduler_names.get(agent_1), gpg_scheduler_names.get(agent_2)},
							            	assumed_politeness).run_two_sided_series(true, true);
					        	}
			        		}
			        	}
			        }
		        }
		        
		        // For adversarial experiments, fix assumed politeness to -1
		        for (Scheduler_GPG sched : gpg_schedulers)
		        {
		        	if (sched instanceof MCTS_Scheduler_GPG)
		        	{
		        		((MCTS_Scheduler_GPG)sched).assumed_politeness_of_other_agents = -1.0;
		        	}
		        }
		        for (Scheduler_GPG sched : gpg_scheduler_clones)
		        {
		        	if (sched instanceof MCTS_Scheduler_GPG)
		        	{
		        		((MCTS_Scheduler_GPG)sched).assumed_politeness_of_other_agents = -1.0;
		        	}
		        }
		        
		        // Adversarial experiments
		        for (int agent_1 = 0; agent_1 < gpg_schedulers.size(); agent_1++)
		        {
		        	for (int agent_2 = agent_1; agent_2 < gpg_schedulers.size(); agent_2++)
		        	{
			        	if (agent_2 == agent_1)
			        	{
			        		// Mirror match
					        new Match_GPG("adversarial_" + gpg_scheduler_names.get(agent_1) + "_vs_" + gpg_scheduler_names.get(agent_2),
					            	numGoalPlanTrees,
					            	AllianceType.ADVERSARIAL,
					            	currentStateGPG.clone(),
					            	new Scheduler_GPG[] {gpg_schedulers.get(agent_1), gpg_scheduler_clones.get(agent_2)},
					            	new String[] {gpg_scheduler_names.get(agent_1), gpg_scheduler_names.get(agent_2) + "_clone"},
					            	-1.0).run_two_sided_series(true, true);
			        	}
			        	else
			        	{
					        new Match_GPG("adversarial_" + gpg_scheduler_names.get(agent_1) + "_vs_" + gpg_scheduler_names.get(agent_2),
					            	numGoalPlanTrees,
					            	AllianceType.ADVERSARIAL,
					            	currentStateGPG.clone(),
					            	new Scheduler_GPG[] {gpg_schedulers.get(agent_1), gpg_schedulers.get(agent_2)},
					            	new String[] {gpg_scheduler_names.get(agent_1), gpg_scheduler_names.get(agent_2)},
					            	-1.0).run_two_sided_series(true, true);
			        	}
		        	}
		        }
	        }
        }
    }
}
