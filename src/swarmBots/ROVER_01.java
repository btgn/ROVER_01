package swarmBots;

import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import common.Communication;
import common.CommunicationHelper;
import common.Coord;
import common.MapTile;
import common.PlanetMap;
import common.Rover;
import common.RoverDetail;
import common.ScanMap;
import common.ScienceDetail;
import enums.RoverConfiguration;
import enums.RoverDriveType;
import enums.RoverMode;
import enums.Terrain;
import rover_logic.Astar;
import rover_logic.SearchLogic;

import enums.RoverToolType;
import controlServer.RoverCommandProcessor;
import controlServer.RoverStats;

/**
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

@SuppressWarnings("unused")
public class ROVER_01 extends Rover {
	



	public ROVER_01() {
		// constructor
		System.out.println("ROVER_01 rover object constructed");
		rovername = "ROVER_01";
	}
	
	public ROVER_01(String serverAddress) {
		// constructor
		System.out.println("ROVER_01 rover object constructed");
		rovername = "ROVER_01";
		SERVER_ADDRESS = serverAddress;
	}
	
	static enum Direction {
		NORTH, SOUTH, EAST, WEST;

		static Map<Character, Direction> map = new HashMap<Character, Direction>() {
			private static final long serialVersionUID = 1L;

			{
				put('N', NORTH);
				put('S', SOUTH);
				put('E', EAST);
				put('W', WEST);
			}
		};

		static Direction get(char c) {
			return map.get(c);
		}
	}

	static class MoveTargetLocation {
		Coord targetCoord;
		Direction d;
	}

	static Map<Coord, Integer> coordVisitCountMap = new HashMap<Coord, Integer>() {
		private static final long serialVersionUID = 1L;

		@Override
		public Integer get(Object key) {
			if (!containsKey(key)) {
				super.put((Coord) key, new Integer(0));
			}
			return super.get(key);
		}
	};

	Coord maxCoord = new Coord(0, 0);

	/**
	 * Connects to the server then enters the processing loop.
	 */
	private void run() throws IOException, InterruptedException {
		 String url = "http://localhost:3000/api";
	        String corp_secret = "gz5YhL70a2";

	        Communication com = new Communication(url, rovername, corp_secret);
		// Make connection to SwarmServer and initialize streams
		Socket socket = null;
		try {
			socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);

			receiveFrom_RCP = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			sendTo_RCP = new PrintWriter(socket.getOutputStream(), true);
			
			// Need to allow time for the connection to the server to be established
			sleepTime = 301;
			
			// Process all messages from server, wait until server requests Rover ID
			// name - Return Rover Name to complete connection
			while (true) {
				String line = receiveFrom_RCP.readLine();
				if (line.startsWith("SUBMITNAME")) {
					//This sets the name of this instance of a swarmBot for identifying the thread to the server
					sendTo_RCP.println(rovername); 
					break;
				}
			}
	
	
			
			/**
			 *  ### Setting up variables to be used in the Rover control loop ###
			 */
			int stepCount = 0;	
			String line = "";	
			boolean goingSouth = false;
			boolean stuck = false; // just means it did not change locations between requests,
									// could be velocity limit or obstruction etc.
			boolean blocked = false;
	
			String[] cardinals = new String[4];
			cardinals[0] = "N";
			cardinals[1] = "E";
			cardinals[2] = "S";
			cardinals[3] = "W";	
			String currentDir = cardinals[0];
			long lastGatherTime = 0;
			long gatherTimePerTile = 3400;
			boolean isGathering = false;
			
			
			/**
			 *  ### Retrieve static values from RCP ###
			 */		
			// **** get equipment listing ****			
			equipment = getEquipment();
			System.out.println(rovername + " equipment list results " + equipment + "\n");
			
			
			// **** Request START_LOC Location from SwarmServer **** this might be dropped as it should be (0, 0)
			StartLocation = getStartLocation();
			System.out.println(rovername + " START_LOC " + StartLocation);
			
			
			// **** Request TARGET_LOC Location from SwarmServer ****
			TargetLocation = getTargetLocation();
			System.out.println(rovername + " TARGET_LOC " + TargetLocation);
			
			
			//Astar aStar = new Astar();

			/**
			 *  ####  Rover controller process loop  ####
			 */
			
			
			while (true) {                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
				
				// **** Request Rover Location from RCP ****
				currentLoc = getCurrentLocation();//will also send the current moving 
				
				
//			        System.err.println("GOT: " + com.getRoverLocations());
			        
//				System.out.println(rovername + " currentLoc at start: " + currentLoc);
				
				// after getting location set previous equal current to be able to check for stuckness and blocked later
				previousLoc = currentLoc;		
				
				

				// ***** do a SCAN *****
				// gets the scanMap from the server based on the Rover current location
				scanMap = doScan(); 
				// prints the scanMap to the Console output for debug purposes
				scanMap.debugPrintMap();
				
		
							
				// ***** get TIMER time remaining *****
				timeRemaining = getTimeRemaining();
				
				MapTile[][] scanMapTiles =scanMap.getScanMap();
				int mapTileCenter = (scanMap.getEdgeSize() - 1) / 2;
				Coord currentLocInMapTile = new Coord(mapTileCenter, mapTileCenter);

				int maxX = currentLoc.xpos + mapTileCenter;
				int maxY = currentLoc.ypos + mapTileCenter;
				if (maxCoord.xpos < maxX && maxCoord.ypos < maxY) {
					maxCoord = new Coord(maxX, maxY);
				} else if (maxCoord.xpos < maxX) {
					maxCoord = new Coord(maxX, maxCoord.ypos);
				} else if (maxCoord.ypos < maxY) {
					maxCoord = new Coord(maxCoord.xpos, maxY);
				}
//				  System.out.println("post message: " + 
				com.postScanMapTiles(currentLoc, scanMapTiles);
//				  );
				/*
				// another call for current location
				SearchLogic search = new SearchLogic();
				//get an item from the available sciences to be harvested
				Coord destination = new Coord(16, 16);
				
				int scanMapSize = scanMap.getEdgeSize();
				
				 List<String> moves = search.Astar(currentLoc, destination, scanMapTiles, RoverDriveType.WHEELS, 
						 jsonToMapUpdateRover(com.getGlobalMap(), currentLoc, scanMapTiles, scanMapSize));
				 */
//				 System.out.println("\n\n\n");
//		         System.out.println(rovername + "currentLoc: " + currentLoc + ", destination: " + destination);
//		         System.out.println(rovername + " moves: " + moves.toString());
//		         System.out.println("\n\n\n");
//		         

		 		

				// ***** MOVING *****
		 		MoveTargetLocation moveTargetLocation = null;
				RoverDetail roverDetail = new RoverDetail();
				ScienceDetail scienceDetail = analyzeAndGetSuitableScience();
				String route = "";
				if (scienceDetail != null) {

					System.out.println("FOUND SCIENCE TO GATHER: " + scienceDetail);

					// The rover is at the location of science, so gather
					if (scienceDetail.getX() == getCurrentLocation().xpos
							&& scienceDetail.getY() == getCurrentLocation().ypos) {
						gatherScience(getCurrentLocation());
						System.out.println("$$$$$> Gathered science " + scienceDetail.getScience() + " at location "
								+ getCurrentLocation());
					} else {

						/*
						RoverConfiguration roverConfiguration = RoverConfiguration.valueOf(rovername);
						RoverDriveType driveType = RoverDriveType.valueOf(roverConfiguration.getMembers().get(0));
						RoverToolType tool1 = RoverToolType.getEnum(roverConfiguration.getMembers().get(1));
						RoverToolType tool2 = RoverToolType.getEnum(roverConfiguration.getMembers().get(2));

						aStar.addScanMap(doScan(), getCurrentLocation(), tool1, tool2);

						char dirChar = aStar.findPath(getCurrentLocation(),
								new Coord(scienceDetail.getX(), scienceDetail.getY()), driveType);
						 */
						
						SearchLogic search = new SearchLogic();
						
						int scanMapSize = scanMap.getEdgeSize();
						
						Coord destination = new Coord(scienceDetail.getX(), scienceDetail.getY());
						
						 List<String> moves = search.Astar(currentLoc, destination, scanMapTiles, RoverDriveType.WHEELS, 
								 jsonToMapUpdateRover(com.getGlobalMap(), currentLoc, scanMapTiles, scanMapSize));

						
						for (String s : moves) {
							route += s + " ";
						}
						route.trim();
						char dirChar = moves.get(0).charAt(0);

						moveTargetLocation = new MoveTargetLocation();
						moveTargetLocation.d = Direction.get(dirChar);

						roverDetail.setRoverMode(RoverMode.GATHER);

						System.out.println("=====> In gather mode using Astar in the direction " + dirChar);
					}

				} else {
					moveTargetLocation = chooseMoveTargetLocation(scanMapTiles, currentLocInMapTile, currentLoc,
							mapTileCenter);

					System.out.println("*****> In explore mode in the direction " + moveTargetLocation.d);

					roverDetail.setRoverMode(RoverMode.EXPLORE);
				}

				if (moveTargetLocation != null && moveTargetLocation.d != null) {
					switch (moveTargetLocation.d) {
					case NORTH:
						moveNorth(route);
						break;
					case EAST:
						moveEast(route);
						break;
					case SOUTH:
						moveSouth(route);
						break;
					case WEST:
						moveWest(route);
						break;
					}

					if (!previousLoc.equals(getCurrentLocation())) {
						coordVisitCountMap.put(moveTargetLocation.targetCoord,
								coordVisitCountMap.get(moveTargetLocation.targetCoord) + 1);
					}
				}
				
				try {
					roverDetail.setRoverName(rovername);
					roverDetail.setX(getCurrentLocation().xpos);
					roverDetail.setY(getCurrentLocation().ypos);

					roverDetail.setDriveType(RoverDriveType.valueOf(RoverConfiguration.ROVER_01.getMembers().get(0)));
					roverDetail.setToolType1(RoverToolType.valueOf(RoverConfiguration.ROVER_01.getMembers().get(1)));
					roverDetail.setToolType2(RoverToolType.valueOf(RoverConfiguration.ROVER_01.getMembers().get(2)));

					sendRoverDetail(roverDetail);

					postScanMapTiles(currentLoc, scanMapTiles);

				} catch (Exception e) {
					System.err.println("Post current map to communication server failed. Cause: "
							+ e.getClass().getName() + ": " + e.getMessage());
				}

				// this is the Rovers HeartBeat, it regulates how fast the Rover
				// cycles through the control loop
				Thread.sleep(sleepTime);

				System.out.println("ROVER_01 ------------ bottom process control --------------");

			
			
				/*
		 		// check gathering done
		 		int centerIndex = (scanMap.getEdgeSize() - 1)/2;
		 		if(lastGatherTime + gatherTimePerTile < System.currentTimeMillis()){
		 			isGathering = false;
			 		// rover will follow highlight path, if no path it will move in one direction
			 		String nextMove = moves.get(0);
			 		switch (nextMove) {
				 	case "N": moveNorth(route); currentDir = "N"; break; 
				 	case "E": moveEast(route); currentDir = "E"; break;
				 	case "W": moveWest(route); currentDir = "W"; break;
				 	case "S": moveSouth(route); currentDir = "S"; break;
				 	default: currentDir = moveStraight(route, currentDir, centerIndex, scanMapTiles); break;		
			 		}
		 		}
	 		*/
		 		/*
				// try moving east 5 block if blocked
				if (blocked) {
					scanMapTiles = scanMap.getScanMap();
					int centerIndex = (scanMap.getEdgeSize() - 1)/2;
				
					
					 if (!scanMapTiles[centerIndex+1][centerIndex].getHasRover() 
								&& scanMapTiles[centerIndex+1][centerIndex].getTerrain() == Terrain.SOIL) {
				
							moveEast(route);
							blocked = false;
							currentDir ="E";
						}
					 else if (!scanMapTiles[centerIndex][centerIndex-1].getHasRover() 
							&& scanMapTiles[centerIndex][centerIndex-1].getTerrain() == Terrain.SOIL) {
//						 System.out.println("Blocked, moving North");
						moveNorth(route);
						blocked = false;
						currentDir ="N";
						
					}
					
					else if (!scanMapTiles[centerIndex][centerIndex+1].getHasRover() 
							&& scanMapTiles[centerIndex][centerIndex+1].getTerrain() == Terrain.SOIL) {
			
//						System.out.println("Blocked, moving South");
						moveSouth(route);
						blocked = false;
						currentDir ="S";
						
					}
					
					else{
						
						moveWest(route);
						
						blocked =false;
						currentDir ="W";
					}
						
					
					
				} else {
					scanMapTiles = scanMap.getScanMap();
					int centerIndex = (scanMap.getEdgeSize() - 1)/2;
					
					if(currentDir =="N"){
						
					
						 if (!scanMapTiles[centerIndex][centerIndex-1].getHasRover() 
									&& scanMapTiles[centerIndex][centerIndex-1].getTerrain() == Terrain.SOIL) {
								moveNorth(route);
//								System.out.println("Not blocked, moving North");
							}
						 else
							 blocked = true;
					}
					else if(currentDir == "S"){
						 if (!scanMapTiles[centerIndex][centerIndex+1].getHasRover() 
								&& scanMapTiles[centerIndex][centerIndex+1].getTerrain() == Terrain.SOIL) {
//							 System.out.println("Not blocked, moving SOuth");
							moveSouth(route);
//							
						}
						 else
							 blocked = true;
					}
						
					else if(currentDir == "E"){
						 if (!scanMapTiles[centerIndex+1][centerIndex].getHasRover() 
								&& scanMapTiles[centerIndex+1][centerIndex].getTerrain() == Terrain.SOIL) {		
							moveEast(route);
						}
						 else 
							 blocked = true;
					}
						
					else{
						if (!scanMapTiles[centerIndex][centerIndex+1].getHasRover() 
								&& scanMapTiles[centerIndex][centerIndex+1].getTerrain() == Terrain.SOIL) {
							moveWest(route);
						}
					 else
						 blocked = true;
					}
				
				
					
						
					
					
				}
				
				*/
				/*
		 		
		 	// ***** GATHERING *****
		 		if (scanMapTiles[centerIndex][centerIndex].getScience().toString().equals("ORGANIC") && !isGathering) {
		 			isGathering = true;
		 			gather();
		 			lastGatherTime = System.currentTimeMillis();
		 		}
		 		*/
	             //////////////////////////
				currentLoc = getCurrentLocation();

	
				// test for stuckness
				stuck = currentLoc.equals(previousLoc);	
				
				// this is the Rovers HeartBeat, it regulates how fast the Rover cycles through the control loop
				//Thread.sleep(sleepTime);
				
//				System.out.println("ROVER_01 ------------ bottom process control --------------"); 
			}  // END of Rover control While(true) loop
		
		// This catch block closes the open socket connection to the server
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
	        if (socket != null) {
	            try {
	            	socket.close();
	            } catch (IOException e) {
	            	System.out.println("ROVER_01 problem closing socket");
	            }
	        }
	    }

	} // END of Rover run thread
	
	private Coord getCoordNorthOf(Coord c) {
		return new Coord(c.xpos, c.ypos - 1);
	}

	private Coord getCoordEastOf(Coord c) {
		return new Coord(c.xpos + 1, c.ypos);
	}

	private Coord getCoordSouthOf(Coord c) {
		return new Coord(c.xpos, c.ypos + 1);
	}

	private Coord getCoordWestOf(Coord c) {
		return new Coord(c.xpos - 1, c.ypos);
	}

	private boolean isBlocked(MapTile[][] mapTiles, Coord c) {
		return mapTiles[c.xpos][c.ypos].getHasRover() || mapTiles[c.xpos][c.ypos].getTerrain() == Terrain.ROCK
				|| mapTiles[c.xpos][c.ypos].getTerrain() == Terrain.NONE;
	}

	private MoveTargetLocation chooseMoveTargetLocation(MapTile[][] scanMapTiles, Coord currentLocInMapTile,
			Coord currentLoc, int mapTileCenter) {
		Coord northCoordInMapTile = getCoordNorthOf(currentLocInMapTile);
		Coord eastCoordInMapTile = getCoordEastOf(currentLocInMapTile);
		Coord southCoordInMapTile = getCoordSouthOf(currentLocInMapTile);
		Coord westCoordInMapTile = getCoordWestOf(currentLocInMapTile);

		Coord northCoord = getCoordNorthOf(currentLoc);
		Coord eastCoord = getCoordEastOf(currentLoc);
		Coord southCoord = getCoordSouthOf(currentLoc);
		Coord westCoord = getCoordWestOf(currentLoc);

		int min = Integer.MAX_VALUE;

		MoveTargetLocation moveTargetLocation = new MoveTargetLocation();

		Stack<Direction> favoredDirStack = getFavoredDirStack(currentLoc, mapTileCenter);

		while (!favoredDirStack.isEmpty()) {
			Direction d = favoredDirStack.pop();
			switch (d) {
			case NORTH:
				if (!isBlocked(scanMapTiles, northCoordInMapTile) && coordVisitCountMap.get(northCoord) < min) {
					min = coordVisitCountMap.get(northCoord);
					moveTargetLocation.targetCoord = northCoord;
					moveTargetLocation.d = Direction.NORTH;
				}
				break;
			case EAST:
				if (!isBlocked(scanMapTiles, eastCoordInMapTile) && coordVisitCountMap.get(eastCoord) < min) {
					min = coordVisitCountMap.get(eastCoord);
					moveTargetLocation.targetCoord = eastCoord;
					moveTargetLocation.d = Direction.EAST;
				}
				break;
			case SOUTH:
				if (!isBlocked(scanMapTiles, southCoordInMapTile) && coordVisitCountMap.get(southCoord) < min) {
					min = coordVisitCountMap.get(southCoord);
					moveTargetLocation.targetCoord = southCoord;
					moveTargetLocation.d = Direction.SOUTH;
				}
				break;
			case WEST:
				if (!isBlocked(scanMapTiles, westCoordInMapTile) && coordVisitCountMap.get(westCoord) < min) {
					min = coordVisitCountMap.get(westCoord);
					moveTargetLocation.targetCoord = westCoord;
					moveTargetLocation.d = Direction.WEST;
				}
			}
		}
		printMoveTargetLocation(moveTargetLocation);
		return moveTargetLocation;
	}

	private Stack<Direction> getFavoredDirStack(Coord currentLoc, int mapTileCenter) {
		int northUnvisitedCount = 0, eastUnvisitedCount = 0, southUnvisitedCount = 0, westUnvisitedCount = 0;
		for (int x = 0; x < currentLoc.xpos; x++) {
			if (coordVisitCountMap.get(new Coord(x, currentLoc.ypos)) == 0) {
				westUnvisitedCount++;
			}
		}
		for (int x = currentLoc.xpos; x < maxCoord.xpos; x++) {
			if (coordVisitCountMap.get(new Coord(x, currentLoc.ypos)) == 0) {
				eastUnvisitedCount++;
			}
		}
		for (int y = 0; y < currentLoc.ypos; y++) {
			if (coordVisitCountMap.get(new Coord(currentLoc.xpos, y)) == 0) {
				northUnvisitedCount++;
			}
		}
		for (int y = currentLoc.ypos; y < maxCoord.ypos; y++) {
			if (coordVisitCountMap.get(new Coord(currentLoc.xpos, y)) == 0) {
				southUnvisitedCount++;
			}
		}
		List<Integer> countList = Arrays.asList(northUnvisitedCount, eastUnvisitedCount, southUnvisitedCount,
				westUnvisitedCount);
		Collections.sort(countList);

		Stack<Direction> directionStack = new Stack<>();

		for (Integer count : countList) {
			if (count == northUnvisitedCount && !directionStack.contains(Direction.NORTH)) {
				directionStack.push(Direction.NORTH);
			}
			if (count == eastUnvisitedCount && !directionStack.contains(Direction.EAST)) {
				directionStack.push(Direction.EAST);
			}
			if (count == southUnvisitedCount && !directionStack.contains(Direction.SOUTH)) {
				directionStack.push(Direction.SOUTH);
			}
			if (count == westUnvisitedCount && !directionStack.contains(Direction.WEST)) {
				directionStack.push(Direction.WEST);
			}
		}
		System.out.println("counts = North(" + northUnvisitedCount + ") East(" + eastUnvisitedCount + ") South("
				+ southUnvisitedCount + ") West(" + westUnvisitedCount + ")");
		// System.out.println("countList = " + countList);
		System.out.println("favoredDirStack = " + directionStack);
		// System.out.println("coordVisitCountMap = " + coordVisitCountMap);

		return directionStack;
	}

	private void printMoveTargetLocation(MoveTargetLocation moveTargetLocation) {
		System.out.println("MoveTargetLocation.x = " + moveTargetLocation.targetCoord.xpos);
		System.out.println("MoveTargetLocation.y = " + moveTargetLocation.targetCoord.ypos);
		System.out.println("MoveTargetLocation.d = " + moveTargetLocation.d);
	}
	
	// ####################### Support Methods #############################
	
	private Map<Coord, MapTile> jsonToMap(JSONArray data) {
		Map<Coord, MapTile> globalMap = new HashMap<>();
    	MapTile tempTile;

    	
        for (Object o : data) {
            JSONObject jsonObj = (JSONObject) o;
            boolean marked = (jsonObj.get("g") != null) ? true : false;
            int x = (int) (long) jsonObj.get("x");
            int y = (int) (long) jsonObj.get("y");
            Coord coord = new Coord(x, y);

            MapTile tile = CommunicationHelper.convertToMapTile(jsonObj);
            globalMap.put(coord, tile); 
        }
        
        return globalMap;
	}
	
	private Map<Coord, MapTile> jsonToMapUpdateRover(JSONArray data, Coord currentLoc, MapTile[][] scanMapTiles, int scanMapSize) {
		Map<Coord, MapTile> globalMap = new HashMap<>();
    	MapTile tempTile;
    	int currentX = currentLoc.getXpos();
    	int currentY = currentLoc.getYpos();
    	int offset = (scanMapSize - 1) / 2;
    	int minX = currentX - offset;
    	int maxX = currentX + offset;
    	int minY = currentY - offset;
    	int maxY = currentY + offset;
    	
        for (Object o : data) {
            JSONObject jsonObj = (JSONObject) o;
            boolean marked = (jsonObj.get("g") != null) ? true : false;
            int x = (int) (long) jsonObj.get("x");
            int y = (int) (long) jsonObj.get("y");
            Coord coord = new Coord(x, y);

            MapTile tile = CommunicationHelper.convertToMapTile(jsonObj);
            if(minX <= x && x <= maxX && minY <= y && y <= maxY){
            	if(scanMapTiles[x - currentX + offset][y - currentY + offset].getHasRover()){
            		tile.setHasRoverTrue();
            	}
            }
            
            globalMap.put(coord, tile); 
        }
        
        return globalMap;
	}
	
	//find a task/science to be harvested
//	private void getNextTask(ArrayList<String> equipment){
//		 String url = "http://localhost:3000/api";
//	        String corp_secret = "gz5YhL70a2";
//
//	        RoverConfiguration rConfig = RoverConfiguration.getEnum("ROVER_01"); 
//             RoverStats rover = new RoverStats(rConfig);
//              
//	        Communication com = new Communication(url, rovername, corp_secret);
//		for (Object o : data) {
//
//            JSONObject jsonObj = (JSONObject) o;
//            String status = (String)jsonObj.get("harvestStatus");
//            
//            if(status.equals("OPEN")){
//            	
//            	
////            	int x = (int) (long) jsonObj.get("x");
////                int y = (int) (long) jsonObj.get("y");
////                Coord coord = new Coord(x, y);
//            }
//            
//		}
//
//	}

	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_01 client;
    	// if a command line argument is present it is used
		// as the IP address for connection to SwarmServer instead of localhost 
		
		if(!(args.length == 0)){
			client = new ROVER_01(args[0]);
		} else {
			client = new ROVER_01();
		}
		
		client.run();
	}
	
	public MapTile getNextTile(String direction, int centerIndex, MapTile[][] scanMapTiles){
		MapTile nextTile = new MapTile();
		switch (direction) {
			case "N": nextTile = scanMapTiles[centerIndex][centerIndex - 1]; break; 
			case "E": nextTile = scanMapTiles[centerIndex + 1][centerIndex]; break;
			case "W": nextTile = scanMapTiles[centerIndex - 1][centerIndex]; break;
			case "S": nextTile = scanMapTiles[centerIndex][centerIndex + 1]; break;
			default: break;	
		}
		
		return nextTile;	
	}
	
	public boolean isBlock(MapTile nextTile){
		return nextTile.getHasRover() || !(nextTile.getTerrain() == Terrain.SOIL || nextTile.getTerrain() == Terrain.GRAVEL);
	}
	
	public String moveStraight(String route, String currentDir, int centerIndex, MapTile[][] scanMapTiles ){
		//moving same direction if not block
		if (!isBlock(getNextTile(currentDir, centerIndex, scanMapTiles))) {
	 		switch (currentDir) {
		 		case "N": moveNorth(route); break; 
		 		case "E": moveEast(route); break;
		 		case "W": moveWest(route); break;
		 		case "S": moveSouth(route); break;
		 		default: break;	
			}
		} else {
			System.out.println("block");
			if (!isBlock(getNextTile("E", centerIndex, scanMapTiles))) {
				moveEast(route);
				currentDir = "E";
			}else if (!isBlock(getNextTile("N", centerIndex, scanMapTiles))) {
				moveNorth(route);
				currentDir = "N";
			}else if (!isBlock(getNextTile("S", centerIndex, scanMapTiles))) {
				moveSouth(route);
				currentDir = "S";
			}else {
				moveWest(route);
				currentDir = "W";
			}
		}
		return currentDir;
	}
}

