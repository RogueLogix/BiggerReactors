package net.roguelogix.biggerreactors.classic.helpers;

import java.util.HashMap;
import java.util.Map;

public class ReactorConversions {

	// Source Reactant Name => Reaction Data
	private static Map<String, ReactorReaction> _reactions = new HashMap<String, ReactorReaction>();
	
	public static void register(String sourceName, String productName) {
		if(_reactions.containsKey(sourceName)) {
			// TODO: 6/22/20 LOG
//			BRLog.warning("Overwriting %s => %s reaction mapping! Someone may be fiddling with Big Reactors game data!", sourceName, productName);
		}
		
		ReactorReaction mapping = new ReactorReaction(sourceName, productName);
		_reactions.put(sourceName, mapping);
	}
	
	public static void register(String sourceName, String productName, float reactivity, float fissionRate) {
		if(_reactions.containsKey(sourceName)) {
			// TODO: 6/22/20 LOG
//			BRLog.warning("Overwriting %s => %s reaction mapping! Someone may be fiddling with Big Reactors game data!", sourceName, productName);
		}
		
		ReactorReaction mapping = new ReactorReaction(sourceName, productName, reactivity, fissionRate);
		_reactions.put(sourceName, mapping);
	}
	
	public static ReactorReaction get(String sourceReactant) {
		if(sourceReactant == null) { return null; }
		return _reactions.get(sourceReactant);
	}
}
