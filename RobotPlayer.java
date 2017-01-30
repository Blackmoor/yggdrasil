package yggdrasil;

import battlecode.common.*;

/*
 * Todo list
 * 
 * Units that move out of sensor range could be followed
 * Archons need to avoid edges and corners when moving
 */
public strictfp class RobotPlayer {
	static final int debugLevel = 0; // 0 = off, 1 = function calls, 2 = logic, 3/4 = detailed info
	static final boolean indicatorsOn = false;	
    static RobotController rc;
    static MapLocation mapCentre;
    static RobotInfo[] robots; //Cached result from senseNearbyRobots
    static TreeInfo[] trees; //Cached result from senseNearbyTree
    static BulletInfo[] bullets; //Cached result from senseNearbyBullets
    static boolean overrideDanger = false;
    static MapLocation rallyPoint = null;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        float x = 0;
        float y = 0;
        int count = 0;
        for (MapLocation m:rc.getInitialArchonLocations(rc.getTeam())) {
        	x += m.x;
        	y += m.y;
        	count++;
        }
        for (MapLocation m:rc.getInitialArchonLocations(rc.getTeam().opponent())) {
        	x += m.x;
        	y += m.y;
        	count++;
        }
        
        if (count == 0)
        	mapCentre = new MapLocation(400,400);
        else 
        	mapCentre = new MapLocation(x/count, y/count);

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runCombat();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case SCOUT:
                runScout();
                break;
            case TANK:
                runCombat();
                break;
        }
	}
    
    static void setIndicator(MapLocation from, MapLocation to, int red, int green, int blue) throws GameActionException {
    	if (indicatorsOn)
    		rc.setIndicatorLine(from, to, red, green, blue);
    }
    
    static void setIndicator(MapLocation centre, int red, int green, int blue) throws GameActionException {
    	if (indicatorsOn)
    		rc.setIndicatorDot(centre, red, green, blue);
    }
    
    static void debug(int level, String str) {
    	if (level <= debugLevel)
    		System.out.println(str);
    }
    
    static float unitStrength(RobotType type) {
    	if (type.bulletSpeed > 0)
    		return type.attackPower;
    	if (type == RobotType.LUMBERJACK)
    		return 1;
    	return 0;
    }
    
    static void sense() throws GameActionException {
    	robots = rc.senseNearbyRobots();
    	trees = rc.senseNearbyTrees();
    	bullets = rc.senseNearbyBullets();
    	overrideDanger = false;
    	
    	float enemyStrength = 0;
    	float allyStrength = 0;
    	
    	RobotInfo enemy = null;
    	for (RobotInfo r: robots) {
    		if (r.getTeam() == rc.getTeam()) {
    			allyStrength += unitStrength(r.getType());
    		} else {
    			if (enemy == null)
    				enemy = r;
    			enemyStrength += unitStrength(r.getType());
    		}
    	}

    	if (enemy != null && (allyStrength < 2 || allyStrength < enemyStrength)) {
    		help(enemy.getLocation());
    	} else {
    		checkHelp();
    	}
    }
    
    static void checkWin() throws GameActionException {
    	// Go for the win if we have enough bullets
    	int vps = rc.getTeamVictoryPoints();
    	float bullets = rc.getTeamBullets();
    	float exchangeRate =  rc.getVictoryPointCost();
    	if (rc.getRoundNum() >= rc.getRoundLimit() -1 || (int)(bullets/exchangeRate) + vps >= GameConstants.VICTORY_POINTS_TO_WIN) {
    		rc.donate(bullets);
    	} else if (bullets > 1000 && rc.getRoundNum() > 200) { //Only donate vps later on - if we get a windfall from a tree then we want to spend the bullets early on	
    		int newVps = (int)((bullets - 1000)/exchangeRate);
    		rc.donate(newVps*exchangeRate);
    	}
    }
    
    /*
     * Broadcast data
     * 0 - set to round num that we broadcast for help (or cancel help)
     * 1 - 1 = goto location (at 2, 3), 0 = cancelled
     * 2 = x location (int part)
     * 3 = y location (int part)
     */
    
    static void help(MapLocation here) throws GameActionException {
    	int round = rc.readBroadcast(0);
    	int rally = rc.readBroadcast(1);
    	if (round == rc.getRoundNum() && rally > 0) //Someone else called for help this turn
    		return;
    	
    	rc.broadcast(0, rc.getRoundNum());
    	rc.broadcast(1, 1);
    	rc.broadcastFloat(2, here.x);
    	rc.broadcastFloat(3, here.y);
    	rallyPoint = here;
    	debug(4, "Calling for help " + here);
    	setIndicator(here, 0, 0, 255);
    }
    
    static void cancelHelp() throws GameActionException {
    	rc.broadcast(0, rc.getRoundNum());
    	rc.broadcast(1, 0);
    	rallyPoint = null;
    	debug(4, "Cancelling help");
    }
    
    static void checkHelp() throws GameActionException {
    	int round = rc.readBroadcast(0);
    	int rally = rc.readBroadcast(1);
    	if (rally > 0) {
    		if (rc.getRoundNum() - round < 50) { //Current help
    			rallyPoint = new MapLocation(rc.readBroadcastFloat(2), rc.readBroadcastFloat(3));
    			if (rc.canSenseLocation(rallyPoint)) {
    				RobotInfo enemy = findNearestRobot(null, rc.getTeam().opponent());
    				if (enemy == null)
    					cancelHelp();
    			}
    		} else {
    			cancelHelp();
    		}
    	} else if (rc.getRoundNum() - round > 1) {
    		//See if someone has broadcast as it wasn't us!
    		MapLocation[] broadcast = rc.senseBroadcastingRobotLocations();
    		if (broadcast.length > 0)
    			help(broadcast[0]);
    		else
    			rallyPoint = null;
    	}
    }
    
    static void checkShake() throws GameActionException {
    	if (!rc.canShake())
    		return;
    	
    	//Check to see if there is a tree in range that we can shake
    	for (TreeInfo t:trees) {
    		if (t.getContainedBullets() > 0) {
    			if (rc.canShake(t.getID()))
	    			rc.shake(t.getID());
    			else {
    				//If we are a scout then head here and try again
    		    	if (rc.getType() == RobotType.SCOUT) {
    		    		tryMove(t.getLocation());
    		    		if (rc.canShake(t.getID()))
    		    			rc.shake(t.getID());
    		    	}
    			}
    			return;
    		}
    	}
    }
    
    static RobotInfo findNearestRobot(RobotType type, Team team) {
    	//List is ordered by distance already so return first entry in list of the correct type
        for (RobotInfo r:robots) {
        	if ((type == null || r.getType() == type) && (team == null || r.getTeam() == team))
        		return r;    	
        }
        return null;
    }
    
    static boolean closeToMapEdge() throws GameActionException {
    	if (rc.onTheMap(rc.getLocation(), RobotType.GARDENER.sensorRadius))
    		return false;
    	return true;
    }
    
    //Check for edges of the map - too close and we don't want to build
    static boolean isGoodGardenLocation() throws GameActionException {
    	return (!closeToMapEdge() && findNearestRobot(RobotType.GARDENER, rc.getTeam()) == null);
    }

    /*
     * Check to see if a bullet fired from here will hit an enemy first (rather than a tree or an ally)
     * If it does return the distance to the enemy or the negative distance for an ally
     * or -100 for a miss or -50 for a hit on a neutral tree
     */
    static float hitDistance(MapLocation loc, Direction dir) {
    	TreeInfo nearestTree = null;
    	float nearestHitTreeDist = -1;
		RobotInfo nearestUnit = null;
		float nearestHitUnitDist = -1;
		
    	//Check each tree to see if it will hit it
		for (TreeInfo t:trees) {
			nearestHitTreeDist = calcHitDist(loc, loc.add(dir, rc.getType().sensorRadius*2), t.getLocation(), t.getRadius());
			if (nearestHitTreeDist >= 0) {
				nearestTree = t;
				break;
			}
		}
		
		for (RobotInfo r:robots) {
			nearestHitUnitDist = calcHitDist(loc, loc.add(dir, rc.getType().sensorRadius*2), r.getLocation(), r.getRadius());
			if (nearestHitUnitDist >= 0) {
				nearestUnit = r;
				break;
			}
		}
    	
		debug(2, "Shot from " + loc + " dir = " + dir + " will hit unit " + nearestUnit + " at distance " + nearestHitUnitDist + " and tree " + nearestTree + " at distance " + nearestHitTreeDist);
		
		if (nearestUnit != null && (nearestTree == null || nearestHitUnitDist <= nearestHitTreeDist)) { //We hit a robot
			if (nearestUnit.getTeam() != rc.getTeam())
				return nearestHitUnitDist;
			else
				return -nearestHitUnitDist;
		}
		
		if (nearestTree != null) {
			if (nearestTree.getTeam() == rc.getTeam().opponent())
				return nearestHitTreeDist;
			else if (nearestTree.getTeam() == rc.getTeam())
				return -nearestHitTreeDist;
			else
				return -50;
		}
		
		return -100;
    }
    
    /*
     * There is one archon that is the prime one - it gets to spend all of the initial bullets
     * The prime is the archon nearest the enemy and is assigned on turn 1
     */
    static boolean amIPrime() {
    	MapLocation[] me = rc.getInitialArchonLocations(rc.getTeam());
    	MapLocation[] them = rc.getInitialArchonLocations(rc.getTeam().opponent());
    	
    	float nearest = 1000;
    	MapLocation prime = null;
    	for (MapLocation m: me) {
    		for (MapLocation t:them) {
    			if (m.distanceTo(t) < nearest) {
    				nearest = m.distanceTo(t);
    				prime = m;
    			}
    		}
    	}
    	
    	return (prime == rc.getLocation());
    }
    
    /*
     * The Archon creates new gardeners and acts as the arbiter for what we build
     * It should avoid bullets and can shake trees
     * If it is the last (or only) archon it tries really hard to stay alive
     */
    static void runArchon() throws GameActionException {
        // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
        try {
	    	boolean prime = amIPrime();
	    	
	    	if (prime) {
	    		debug(1, "I'm the prime archon!");
			} else {
		        debug(1, "I'm an archon!");
			}
	        
	        // The code you want your robot to perform every round should be in this loop
	        while (true) {        	

            	checkWin();
            	sense();
            	checkShake();
            	
            	if (rc.getRoundNum() == 2 && rc.getTeamBullets() >= GameConstants.BULLETS_INITIAL_AMOUNT) //Check to see if the prime failed to build and take over
            		prime = true;
            	
            	RobotInfo nearestGardener = findNearestRobot(RobotType.GARDENER, rc.getTeam());
            	RobotInfo nearestEnemy = null;
            	for (RobotInfo r:robots) {
            		if (r.getTeam() != rc.getTeam() && r.getType().canAttack()) {
            			nearestEnemy = r;
            			break;
            		}
            	}

            	float resourcesNeeded = RobotType.GARDENER.bulletCost;
            	
            	if (nearestEnemy != null)
            		resourcesNeeded += RobotType.SOLDIER.bulletCost;
            	else
            		resourcesNeeded += GameConstants.BULLET_TREE_COST;
            	
            	if (((prime && rc.getRoundNum() <= 2) || rc.getRoundNum() > 25) && rc.getTeamBullets() >= resourcesNeeded && nearestGardener == null) {
	                Direction dir = rc.getLocation().directionTo(mapCentre);
	                if (dir == null)
	                	dir = randomDirection();

	                int directionsToTry = 60; //We have plenty of time to try building and in a confined place - options are good
                	for (int i=0; i<directionsToTry; i++) {
    	                if (rc.canHireGardener(dir)) {
    	                	rc.hireGardener(dir);
    	                    break;
    	                }
            			dir = dir.rotateLeftRads((float)Math.PI*2/directionsToTry);
            		}	                    
                }
            	
                wander();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();
	        }
        } catch (Exception e) {
            debug(1, "Archon Exception");
            e.printStackTrace();
        }
    }
    
    static final int buildDirections = 20;
    
    static int countBuildOptions(Direction dir) {
    	int result = 0;
    	if (dir == null)
    		dir = rc.getLocation().directionTo(mapCentre);
    	if (dir == null)
    		dir = randomDirection();
    	
    	for (int i=0; i<buildDirections; i++) {
    		if (rc.canBuildRobot(RobotType.LUMBERJACK, dir)) //A neutral tree means we need a lumberjack
     			result++;
			dir = dir.rotateLeftRads((float)Math.PI*2/buildDirections);
		}
    	
    	return result;
    }

    static boolean buildIt(RobotType type, Direction dir) throws GameActionException {
    	debug(1, "buildIt: type = " + type + " dir = " + dir);
    	if (dir == null)
    		dir = rc.getLocation().directionTo(mapCentre);
    	if (dir == null)
    		dir = randomDirection();
    	
		for (int i=0; i<buildDirections; i++) {
			if (rc.canBuildRobot(type, dir)) {
				rc.buildRobot(type, dir);
				return true;
			}
			dir = dir.rotateLeftRads((float)Math.PI*2/buildDirections);
		}
		return false;
    }
    
    /*
     * Gardeners create trees and keep them watered
     * If there are no other gardeners in sight then build a garden here otherwise move away from nearest gardener
     * 
     * Once a garden centre is picked we create a circle of trees within watering distance
     */
	static void runGardener() throws GameActionException {
        debug(1, "I'm a gardener!");
        Team me = rc.getTeam();
        final int sides = 9;
        final float buildDist = (float) (1 / Math.sin(Math.PI/sides)) - RobotType.GARDENER.bodyRadius - GameConstants.BULLET_TREE_RADIUS;
    	boolean scoutBuilt = false;
    	boolean movedAway = false;
    	MapLocation centre = null;
    	Direction spokes[] = new Direction[sides-2];
        MapLocation plantFrom[] = new MapLocation[sides-2];
        MapLocation treeCentre[] = new MapLocation[sides-2];
        Direction buildDir = null;
        MapLocation buildLoc = null;
    	
        // The code you want your robot to perform every round should be in this loop
        while (true) {
        	
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	checkWin();         	
                sense();
            	checkShake();
            	
            	if (centre == null && isGoodGardenLocation()) {
	            	//We centre ourselves here and build a circle of trees around us on the points of a 9 sided shape with 2 trees missing to leave space to move
	                //This shape allows us to water any tree from the centre without moving
            		centre = rc.getLocation();
	                buildDir = centre.directionTo(mapCentre).opposite(); 
	                buildLoc = centre.add(buildDir, RobotType.GARDENER.strideRadius);
	                
	                spokes[0] = buildDir.rotateLeftRads((float)(Math.PI*3/sides));
	                for (int i=1; i<spokes.length; i++ ) {
	                	spokes[i] = spokes[i-1].rotateLeftRads((float)(Math.PI*2/sides));
	                }
	         
	                for (int i=0; i<spokes.length; i++ ) {
	                	plantFrom[i] = centre.add(spokes[i], buildDist);
	                	if (rc.onTheMap(plantFrom[i], RobotType.GARDENER.bodyRadius)) {
	                		treeCentre[i] = plantFrom[i].add(spokes[i], RobotType.GARDENER.bodyRadius + GameConstants.BULLET_TREE_RADIUS + GameConstants.GENERAL_SPAWN_OFFSET);
	                		if (!rc.onTheMap(treeCentre[i], GameConstants.BULLET_TREE_RADIUS)) {
	                			plantFrom[i] = null; //Cannot build in this direction
	                		}
	                	} else {
	                		plantFrom[i] = null; //Cannot build in this direction
	                	}
	                }
            	}
                
            	if (centre == null) { //Move away from nearestGardener and randomly thereafter 
            		if (!movedAway) {
	                	RobotInfo nearestGardener = findNearestRobot(RobotType.GARDENER, rc.getTeam());
	                	if (nearestGardener != null) {
	                		wanderDir = nearestGardener.getLocation().directionTo(rc.getLocation());
	                		movedAway = true;
	                	}
            		}
                	wander();
            	}
            	
            	int defenders = 0;
                int lumberjacks = 0;
                int scouts = 0; // Number of enemy scouts
            	int numTrees = 0;
            	int containerTrees = 0;
            	int myTrees = 0;

            	for (TreeInfo t:trees) {
            		if (t.getTeam() == me)
            			myTrees++;
            		else
            			numTrees++;
            		if (t.containedRobot != null)
            			containerTrees++;
            	}
                
            	RobotInfo nearestEnemy = null;
                for (RobotInfo r: robots) {
                	if (r.getTeam() != me) {
                		if (nearestEnemy == null)
                			nearestEnemy = r;
                		if (r.getType() == RobotType.SCOUT)
                			scouts++;
                	} else {
                		if (r.getType() == RobotType.LUMBERJACK)
                			lumberjacks++;
                		else if (r.getType() == RobotType.SOLDIER || r.getType() == RobotType.TANK)
                			defenders++;
                	}
                }
            	
                //Check to see if we want to build anything
                if (rc.isBuildReady()) {
                	int buildLocations = countBuildOptions(buildDir);
                	
                	if (buildLocations <= 1 || (containerTrees > 0 && lumberjacks == 0)) { //We need a lumberjack as a priority
                		if (rc.hasRobotBuildRequirements(RobotType.LUMBERJACK)) {
                			tryMove(buildLoc);
                			buildIt(RobotType.LUMBERJACK, buildDir);
                		}
                	}
                	
	                if (defenders == 0) {
	                	if (rc.isBuildReady() && rc.hasRobotBuildRequirements(RobotType.TANK)) {
	                		tryMove(buildLoc);
	                		buildIt(RobotType.TANK, buildDir);
	                	}
	                	
	                	if (rc.isBuildReady() && rc.hasRobotBuildRequirements(RobotType.SOLDIER)) {
	            			tryMove(buildLoc);
	                		buildIt(RobotType.SOLDIER, buildDir);
	                	}
	                }
	                
	                if (rc.hasRobotBuildRequirements(RobotType.SCOUT) && !scoutBuilt) {
	                	tryMove(buildLoc);
                		scoutBuilt = buildIt(RobotType.SCOUT, buildDir);
	                }
	                
	                if (rc.isBuildReady() && rc.hasRobotBuildRequirements(RobotType.LUMBERJACK) && (numTrees > lumberjacks || scouts > lumberjacks)) {
                		tryMove(buildLoc);
            			buildIt(RobotType.LUMBERJACK, buildDir);
	                }
                }

	            //See if we can plant a tree this turn
                if (centre != null && (myTrees == 0 || defenders > 0) && rc.hasTreeBuildRequirements() && !rc.hasMoved()) {
                	for (int currentSpoke=0; currentSpoke<spokes.length; currentSpoke++) {               
                		if (plantFrom[currentSpoke] != null && canMove(plantFrom[currentSpoke]) && !rc.isCircleOccupiedExceptByThisRobot(treeCentre[currentSpoke], GameConstants.BULLET_TREE_RADIUS)) {
                			rc.move(plantFrom[currentSpoke]);
                			if (rc.getLocation() == plantFrom[currentSpoke] && rc.canPlantTree(spokes[currentSpoke])) {
                				rc.plantTree(spokes[currentSpoke]);
                			}
                			break;
                		}
	                }
                }
                
            	if (!rc.hasMoved() && centre != null)
            		tryMove(centre);
                
                if (rc.canWater()) {
	                TreeInfo waterMe = null;
	                for (TreeInfo t:trees) { //Find tree most in need of water  
                		if (t.getTeam() == rc.getTeam() && rc.canWater(t.getID()) && (waterMe == null || (t.getHealth() < waterMe.getHealth())))
                			waterMe = t;
	                }
	                if (waterMe != null)
	                	rc.water(waterMe.getID());
                }
                
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                debug(1, "Gardener Exception");
                e.printStackTrace();
            }
        }
    }
	
	/*
	 * Track a bullet
	 * Return 0 if is misses, 1 if it hits an enemy, -1 if it hits an ally, -2 if it can be dodged, -3 if it hits a neutral
	 */
	static int processShot(Direction dir, RobotInfo target) {
		float hitDist = hitDistance(rc.getLocation().add(dir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET), dir);
		if (hitDist == -100) //Miss
			return 0;
		
		if (hitDist == -50) //Neutral
			return -3;
		
		if (hitDist < 0) //Ally
			return -1;
		
		int turnsBeforeHit = (int) Math.ceil(hitDist/ rc.getType().bulletSpeed);
		if (hitDist % rc.getType().bulletSpeed == 0)
			turnsBeforeHit--;
		if (turnsBeforeHit * target.getType().strideRadius <= target.getType().bodyRadius)
			return 1;
		
		return -2; //Bullet will probably miss (can be dodged)
	}
	
	static boolean canShoot(RobotInfo target) {		
		float ammo = rc.getTeamBullets();
		if (rc.getAttackCount() > 0)
			return false;
		if (target.getType() == RobotType.ARCHON && ammo < 500)
			return false;
		//if (rc.getRoundNum() > 200 && ammo < GameConstants.BULLET_TREE_COST + 10)
		//	return false;
		return (ammo >= 1);
	}

	/*
	 * shoot works out the optimum fire pattern based on the size of the target and its distance from us then shoots
	 * avoiding friendly fire
	 * 
	 * If the enemy is really close we may have a guaranteed hit of one or more bullets depending on its stride
	 * If it is further away we may need to fire multiple shots to guarantee hitting it with one bullet
	 */
	static void shoot(RobotInfo target) throws GameActionException {
		debug(1, "Shooting at " + target);

		if (target == null || !canShoot(target))
			return;
		
		MapLocation targetLoc = target.getLocation();
		MapLocation myLocation = rc.getLocation();
    	Direction dir = myLocation.directionTo(targetLoc);
    	float dist = myLocation.distanceTo(targetLoc);  
    	int shot = processShot(dir, target);
    	
    	if (shot == 0) //Miss
    		return;
    	if (shot == -1) //Hit an ally
    		return;
    	if (shot == -3) //Neutral
    		return;
    	
    	//Look at the distance to target and its size to determine if it can dodge
    	//Pentad fires 5 bullets with 15 degrees between each one (spread originating from the centre of the robot firing)
    	//Triad fires 3 bullets with 20 degrees between each one
    	//We can work out the angle either side of the centre of the target at which we hit
    	float spreadAngle = (float) Math.asin(target.getType().bodyRadius/dist);
    	int shotsToFire = 0;
    	Direction shotDir = dir;
    	debug(3, "shoot: target " + target +  " dist=" + dist + " spreadAngle = " + spreadAngle + " (" + Math.toDegrees((double)spreadAngle) + ")");
    	if (shot == -2) { //can be dodged
    		if (rc.canFireTriadShot() && dist <= target.getType().bodyRadius / Math.sin(Math.toRadians(GameConstants.TRIAD_SPREAD_DEGREES/2))) {
    			shotsToFire = 3;
    			debug (3, "Firing 3 - 1 should hit");
    		} else if (rc.canFirePentadShot() && dist <= target.getType().bodyRadius / Math.sin(Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES/2))) {
    			shotsToFire = 5;
    			debug (3, "Firing 5 - 1 should hit");
    		}
    	} else if (rc.canFirePentadShot() && 2*spreadAngle >= Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES*4)) { //All 5 shots will hit
    		shotsToFire = 5;
    		debug (3, "Firing 5 - all should hit");
    	} else if (rc.canFirePentadShot() && 2*spreadAngle > Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES*3)) { //4 shots will hit
    		shotsToFire = 5;
    		shotDir.rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES/2);
    		debug (3, "Firing 5 - 4 should hit");
    	} else if (rc.canFireTriadShot() && 2*spreadAngle > Math.toRadians(GameConstants.TRIAD_SPREAD_DEGREES*2)) { //All 3 triad shots will hit
    		shotsToFire = 3;
    		debug (3, "Firing 3 - all should hit");
    	} else if (rc.canFirePentadShot() && 2*spreadAngle > Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES*2)) { //3 of 5 shots will hit)
    		shotsToFire = 5;
    		debug (3, "Firing 5 - 3 should hit");
    	} else if (rc.canFireTriadShot() && 2*spreadAngle > Math.toRadians(GameConstants.TRIAD_SPREAD_DEGREES*2)) { //2 of a triad shots will hit
    		shotsToFire = 3;
    		shotDir.rotateLeftDegrees(GameConstants.TRIAD_SPREAD_DEGREES/2);
    		debug (3, "Firing 3 - 2 should hit");
    	} else if (rc.canFirePentadShot() && 2*spreadAngle > Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES)) { //2 of 5 shots will hit
    		shotsToFire = 5;
    		shotDir.rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES/2);
    		debug (3, "Firing 5 - 2 should hit");
    	} else if (rc.canFireSingleShot() && shot == 1) {
    		shotsToFire = 1;
    		debug (3, "Firing 1 shot");
    	}
    	
    	if (shotsToFire == 5) {
    		rc.firePentadShot(shotDir);
    		debug(2, "Shooting 5 shots at " + target);
    	} else if (shotsToFire == 3) {
    		rc.fireTriadShot(shotDir);
    		debug(2, "Shooting 3 shots at " + target);
    	} else if (shotsToFire == 1) {
    		rc.fireSingleShot(shotDir);
    		debug(2, "Firing 1 shot at " + target);
		}
    	if (shotsToFire > 0) { //We shot so update bullet info
    		bullets = rc.senseNearbyBullets();
    	} else {
    		debug(2, "Not shooting at " + target + " dist=" + dist);
    	}
	}
	
	
    static void runCombat() throws GameActionException {
        debug(1, "I'm a combatant!");
        int turnsToWander = 0;

        // The code you want your robot to perform every round should be in this loop
        while (true) {       	
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	checkWin();
            	sense();
            	checkShake();
                boolean stay = manageCombat();
                if (!stay) {
                	MapLocation here = rc.getLocation();
                	
                	if (turnsToWander > 0)
                		turnsToWander--;
                	
                	if (rallyPoint != null && turnsToWander == 0) {
                		if (wallMode)
                			wallMove(rallyPoint);
                		else if (!tryMove(rallyPoint) || (rc.getType() == RobotType.TANK && rc.getLocation() == here && trees.length > 0 && trees[0].getTeam() == rc.getTeam()))
		                    engageWallMode(rallyPoint);
                	}
                	wander();
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                debug(1, "Combatant Exception");
                e.printStackTrace();
            }
        }
    }
    
    static float lumberjackRange() {
    	return rc.getType().bodyRadius + RobotType.LUMBERJACK.strideRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS;
    }
    
    static int lumberjacksInRange(MapLocation loc) {
    	int enemyLumberjacks = 0;
    	int allyLumberjacks = 0;
    	int enemies = 0;
    	for (RobotInfo r:robots) {
    		if (r.getTeam() != rc.getTeam())
    			enemies++;
    		if (r.getType() == RobotType.LUMBERJACK && loc.distanceTo(r.getLocation()) <= lumberjackRange()) {
    			if (r.getTeam() != rc.getTeam())
    				enemyLumberjacks++;
    			else
    				allyLumberjacks++;
    		}
    	}
    	
    	if (overrideDanger)
    		enemyLumberjacks = 0;
    	
    	if (enemies > 0) //Our own lumberjacks will be attacking
    		enemyLumberjacks += allyLumberjacks;
    	
    	return enemyLumberjacks;
    }
    
    /*
     * A tree is safe if we can hide in it
     * That means there has to be room and there is no attacking robot within its stride distance since a shot fired adjacent to a tree could hit us in the tree
     */
    static boolean isTreeSafe(TreeInfo t) throws GameActionException {
    	if (Clock.getBytecodesLeft() < 1000) //This routine can take time so return false if we are short on time
    		return false;
    	
    	if (t.getHealth() <= GameConstants.LUMBERJACK_CHOP_DAMAGE || (t.containedRobot != null && t.getHealth() < 20))
    		return false;
    	
    	if (t.getRadius() < RobotType.SCOUT.bodyRadius) //Too small to hide in
    		return false;
    	
    	//For trees the same size as us they are safe if no lumberjack is in range and no unit with bullets is within a stride of the tree edge
    	for (RobotInfo r: robots) {
    		float distanceToTree = r.getLocation().distanceTo(t.getLocation());
    		float gapBetween = distanceToTree - t.getRadius() - r.getType().bodyRadius;
    		if (gapBetween < 0) { //Occupied
    			setIndicator(t.getLocation(),255,128,128);
    			return false;
    		}

    		if (r.getTeam() != rc.getTeam() && r.getType().canAttack()) { //Enemy
    			float dangerDist = r.getType().strideRadius;
    			if (r.getType() == RobotType.LUMBERJACK)
    				dangerDist += GameConstants.LUMBERJACK_STRIKE_RADIUS;
    			else
    				dangerDist += GameConstants.BULLET_SPAWN_OFFSET;
    			if (gapBetween <= dangerDist) {
    				setIndicator(t.getLocation(), 255,0,0);
    				return false;
    			}
    		}
    	}
    	setIndicator(t.getLocation(),0,255,0);
    	return true;
    }
    
    /*
     * ManageCombat
     * 
     * Find the best target and the best position to shoot from
     * Returns true if we are in the right place and shouldn't move
     * 
     * We track the position of the nearest threat and the position of the best target
     */
    static boolean manageCombat() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        MapLocation myLocation = rc.getLocation();
        RobotInfo nearestGardener = null;
        RobotInfo nearestArchon = null;
        RobotInfo nearestDanger = null;
        RobotInfo nearestEnemy = null;
        RobotInfo nearestLumberjack = null;
        RobotInfo nearestAllyLumberjack = null;
        float safeDistance = lumberjackRange() + GameConstants.BULLET_SPAWN_OFFSET;
    	
        for (RobotInfo r:robots) {
        	if (r.getTeam() == enemy) {
        		if (nearestGardener == null && r.getType() == RobotType.GARDENER)
        			nearestGardener = r;
        		else if (nearestArchon == null && r.getType() == RobotType.ARCHON)
        			nearestArchon = r;
        		else if (nearestLumberjack == null && r.getType() == RobotType.LUMBERJACK)
        			nearestLumberjack = r;
        		if (nearestDanger == null && r.getType().canAttack())
        			nearestDanger = r;
        		if (nearestEnemy == null)
        			nearestEnemy = r;
        	} else {
        		if (nearestAllyLumberjack == null && r.getType() == RobotType.LUMBERJACK)
        			nearestAllyLumberjack = r;
        	}
        }
        
        if (nearestEnemy == null)
        	return false;
        
        if (rc.getType() == RobotType.SCOUT && damageAtLocation(rc.getLocation()) > 0) {
        	//We are better off dodging by moving away
        	MapLocation away = rc.getLocation().add(nearestEnemy.getLocation().directionTo(rc.getLocation()), RobotType.SCOUT.strideRadius);
        	tryMove(away);
        	return false;
        }

    	MapLocation combatPosition = null; 
    	
        // If there is a threat ...
        if (nearestDanger != null) {
        	MapLocation dangerLoc = nearestDanger.getLocation();
        	
            //If we are a scout then find the nearest available tree to shoot from
        	//Must be close to target and us and safe
            TreeInfo nearestTree = null;
            
            if (rc.getType() == RobotType.SCOUT) {
            	TreeInfo[] near = rc.senseNearbyTrees(dangerLoc, -1, null);
	            for (TreeInfo t:near) {
	            	if (isTreeSafe(t)) {
	            		nearestTree = t;
	            		break;
	            	}
	            }
            }
            
        	if (nearestTree != null) { //Scouts can hide in trees
        		float bulletOffset = GameConstants.BULLET_SPAWN_OFFSET / 2;
            	float dist = nearestTree.radius - RobotType.SCOUT.bodyRadius - bulletOffset;
            	debug(2, "Hiding in tree " + nearestTree + " target = " + nearestDanger);
            	if (dist >= 0)
            		combatPosition = nearestTree.getLocation().add(nearestTree.getLocation().directionTo(dangerLoc),dist);
            	else
            		combatPosition = nearestTree.getLocation().add(nearestTree.getLocation().directionTo(dangerLoc).opposite(),-dist);	                		
        	} else if (nearestDanger.getType() == RobotType.LUMBERJACK || (nearestLumberjack != null && myLocation.distanceTo(nearestLumberjack.getLocation()) < safeDistance)) {
        		debug(2, "Keeping at range from enemy lumberjack" + nearestLumberjack);
            	combatPosition = nearestLumberjack.getLocation().add(nearestLumberjack.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
        	} else if (canShoot(nearestDanger) && canBeat(nearestDanger)) {
        		RobotType t = nearestDanger.getType();
        		debug(2, "Can Win: Closing on " + nearestDanger);
        		overrideDanger = true;
       		
    			TreeInfo tr = null;
    			if (rc.canSenseLocation(nearestDanger.getLocation()))
    				tr = rc.senseTreeAtLocation(nearestDanger.getLocation());
    			if (tr == null) {
            		safeDistance = rc.getType().bodyRadius + t.bodyRadius; //Right up close and personal
            		combatPosition = dangerLoc.add(dangerLoc.directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
    			} else { //Move to edge of tree closest to scout
    				safeDistance = rc.getType().bodyRadius + tr.getRadius(); //edge of tree
    				if (nearestDanger.getLocation() == tr.getLocation()) {    					
                		combatPosition = dangerLoc.add(dangerLoc.directionTo(myLocation), safeDistance);
    				} else {
    					combatPosition = tr.getLocation().add(tr.getLocation().directionTo(nearestDanger.getLocation()), safeDistance);
    				}
    			}       		
        	} else {
            	debug(2, "Running from " + nearestDanger);
            	RobotType t = nearestDanger.getType();
            	safeDistance = t.sensorRadius + rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET;
            	combatPosition = nearestDanger.getLocation().add(nearestDanger.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);    	
        	}
        } else if (nearestAllyLumberjack != null && nearestEnemy != null && myLocation.distanceTo(nearestAllyLumberjack.getLocation()) < safeDistance) {
    		debug(2, "Keeping at range from our lumberjack" + nearestAllyLumberjack);
        	combatPosition = nearestAllyLumberjack.getLocation().add(nearestAllyLumberjack.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
    	} else { //Not in danger so close with best enemy target
        	RobotInfo target = null;

        	if (nearestGardener != null)
        		target = nearestGardener;
        	else if (nearestArchon != null)
        		target = nearestArchon;
        	if (target != null) {
        		debug(2, "Safe: Closing on " + target);
        		combatPosition = target.getLocation().add(target.getLocation().directionTo(myLocation).rotateLeftDegrees(5), rc.getType().bodyRadius + target.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);
        	}
        }
        
        if (combatPosition != null) {
        	wallMode = false;
        	tryMove(combatPosition);
        }
        
        //Now find nearest target to new position
        RobotInfo[] targets = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (targets.length > 0) {
        	shoot(targets[0]);
        }
        
        return true;
    }
    
    static boolean canBeat(RobotInfo enemy) {
    	if (!rc.getType().canAttack())
    		return false;
    	
    	if (!enemy.getType().canAttack())
    		return true;

    	if (rc.getType() == RobotType.LUMBERJACK) { //These can't shoot so run away from soldiers and tanks
    		if (enemy.getType() == RobotType.SOLDIER || enemy.getType() == RobotType.TANK)
    			return false;
    	}
    	
    	if (rc.getType() == RobotType.SCOUT && enemy.getType() != RobotType.SCOUT) //Assume we will lose as we are better off finding non-combat units to shoot
    		return false;
    	
    	int turnsToKill = (int) (enemy.getHealth() / rc.getType().attackPower);
    	int turnsToDie = (int) (rc.getHealth() / enemy.getType().attackPower);
    	return turnsToKill <= turnsToDie;
    }
    
    /*
     * Scouts are used to kill from the safety of trees
     * Once in a tree (just back from the edge) our shots will safely fire but the enemy will hit the tree!
     * Lumberjacks are our nemesis as they can strike us in the trees so we always move away from them
     * Even our own lumberjacks will hit us if they can see an enemy
     */
    static void runScout() throws GameActionException {
        debug(1, "I'm a scout!");
        Team enemy = rc.getTeam().opponent();
        MapLocation[] archons = rc.getInitialArchonLocations(enemy);
        int archon_to_visit = 0;
        //Work out which of our archons we are nearest
        MapLocation[] myArchons = rc.getInitialArchonLocations(rc.getTeam());      
        int nearest = -1;
        int i = 0;
        for (MapLocation m: myArchons) {
        	if (nearest == -1 || rc.getLocation().distanceTo(m) < rc.getLocation().distanceTo(myArchons[nearest]))
        		nearest = i;
        	i++;
        }
        
        // The code you want your robot to perform every round should be in this loop
        while (true) {        	
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	checkWin();
            	sense();
            	checkShake();
                boolean stay = manageCombat();
                // Move towards current enemy archon position
                if (!stay && !rc.hasMoved()) {
                	if (archon_to_visit >= archons.length) {
                		wander();
                	} else {
                		if (rc.getLocation().distanceTo(archons[(archon_to_visit+nearest)%archons.length]) < rc.getType().strideRadius)
	                		archon_to_visit++;             		
	                	moveTo(archons[(archon_to_visit+nearest)%archons.length]);
	                	debug(2, "Moving to next archon " + archons[(archon_to_visit+nearest)%archons.length]);
                	}
                }
                
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                debug(1, "Scout Exception");
                e.printStackTrace();
            }
        }
    }
    
    static void moveTowards(RobotInfo target) throws GameActionException {
    	MapLocation here = rc.getLocation();
    	Direction dir = target.getLocation().directionTo(here).rotateRightDegrees(5);
    	float dist = target.getType().bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS / 2;
    	
    	tryMove(target.getLocation().add(dir, dist));
    }

    static void runLumberjack() throws GameActionException {
        debug(1, "I'm a lumberjack!");
        Team me = rc.getTeam();
        Team enemy = me.opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	checkWin();
            	sense();
            	checkShake();
            	
            	RobotInfo nearestEnemy = findNearestRobot(null, enemy);
            	if (nearestEnemy != null)
            		overrideDanger = canBeat(nearestEnemy);
            	else
            		overrideDanger = false;
            	
            	debug(2, "Nearest enemy = " + nearestEnemy + " can beat = " + overrideDanger);
            	
            	// See if there are any enemy robots within striking range - we only attack enemies we can beat - otherwise we retreat
            	if (damageAtLocation(rc.getLocation()) > 0 && !overrideDanger) {
            		wallMode = false;
            		MapLocation away = null;
            		if (nearestEnemy == null) { //Nothing in sight - must be bullets
            			away = rc.getLocation().add(randomDirection(), RobotType.LUMBERJACK.strideRadius);
            		} else {
            			away = rc.getLocation().add(nearestEnemy.getLocation().directionTo(rc.getLocation()), RobotType.LUMBERJACK.strideRadius);
            		}
            		debug(2, "Dodging to safety");
            		tryMove(away, 18, 10, 10);
            	} else if (nearestEnemy != null) { //We can beat it so close and attack
            		float distance = rc.getLocation().distanceTo(nearestEnemy.getLocation());
            		debug(2, "Moving towards " + nearestEnemy + " current distance = " + distance);
	                if (distance > GameConstants.LUMBERJACK_STRIKE_RADIUS + nearestEnemy.getType().bodyRadius) {
	                	debug(2, "Moving towards " + nearestEnemy);
	                	moveTowards(nearestEnemy);
	                }
	                
	                RobotInfo[] local = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
	                if (local.length > 0) {
	                    // Use strike() to hit all nearby robots!
	                	debug(2, "Striking at " + local.length + " enemies");
	                    rc.strike();
	                }
            	}
            	
            	// We love chopping trees - prioritise neutrals containing units, then enemy trees, then empty neutrals
                boolean chopped = false;
                
            	if (!rc.hasAttacked()) {
	            	TreeInfo bestTrapped = null; //Nearest Neutral containing a unit
	            	TreeInfo bestEnemy = null; //Nearest enemy tree
	            	TreeInfo bestNeutral = null; //Nearest neutral
	                
	            	for (TreeInfo t:trees) {
	            		if (t.getTeam() == Team.NEUTRAL) {
	            			if (t.getContainedRobot() != null && bestTrapped == null)
	            				bestTrapped = t;
	            			if (bestNeutral == null)
	            				bestNeutral = t;
	            		} else if (t.getTeam() == enemy && bestEnemy == null)
	            			bestEnemy = t;
	            	}
	            	TreeInfo best = bestTrapped;
	            	if (best == null)
	            		best = bestEnemy;
	            	if (best == null)
	            		best = bestNeutral;
	            	if (best != null) {
	            		if (rc.canChop(best.getID())) {
	            			rc.chop(best.getID());
	            			chopped = true;
	            		} else { //Move to best and chop something if we can
	            			Boolean blocked = false;
	            			Direction dir = rc.getLocation().directionTo(best.getLocation());
	            			float dist = rc.getLocation().distanceTo(best.getLocation()) - best.getRadius() - RobotType.LUMBERJACK.bodyRadius;
	            			MapLocation chopLoc = rc.getLocation().add(dir, dist);
	            			if (wallMode)
	            				wallMove(chopLoc);
	            			else
	            				blocked = !tryMove(chopLoc);
	            			
	            			if (bestTrapped != null && rc.canChop(bestTrapped.getID())) {
	                			rc.chop(bestTrapped.getID());
	                			chopped = true;
	            			} else if (bestEnemy != null && rc.canChop(bestEnemy.getID())) {
	                			rc.chop(bestEnemy.getID());
	                			chopped = true;
	            			} else if (bestNeutral != null && rc.canChop(bestNeutral.getID())) {
	                			rc.chop(bestNeutral.getID());
	                			chopped = true;
	            			}
	            			if (blocked && !chopped)
	            	    		engageWallMode(chopLoc);
	            		}
	            	}
            	}
            	
                if (!rc.hasMoved() && !chopped) {
                	wander();
                }
                
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                debug(1, "Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
    
    static TreeInfo getTreeAtLocation(MapLocation loc) {
    	TreeInfo[] nearTrees = rc.senseNearbyTrees(loc, rc.getType().bodyRadius, null);
    	if (nearTrees.length == 0)
    		return null;
    	return nearTrees[0];
    }
    
    static boolean withinTree(TreeInfo tree) {
    	return (rc.getLocation().distanceTo(tree.getLocation()) < tree.getRadius() - rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);   		
    }
    
    /*
     * damageAtLocation
     * 
     * Works out if we will be shot at the given location
     * or if a lumberjack is in range (ours or the enemies!)
     * or if an enemy going after us can fire and hit us this turn
     * 
     * If we are a scout and the location is in a tree we are safe from bullets
     * 
     * Returns amount of damage we would take at this location
     */
    static float damageAtLocation(MapLocation loc) throws GameActionException {
        float damage = 0;
        
        int lumberjacks = lumberjacksInRange(loc);
    	if (lumberjacks > 0) {
    		setIndicator(loc, 255, 0, 128);
    		damage += lumberjacks * RobotType.LUMBERJACK.attackPower;
    	}
    	
    	if (rc.getType() == RobotType.SCOUT) {
    		TreeInfo tree = getTreeAtLocation(loc);
    		if (tree != null && withinTree(tree) && isTreeSafe(tree)) {
	    		setIndicator(loc, 0, 255, 128);
	    		return damage;
    		}
    	}
  	
    	float bullets = bulletDamage(loc);
    	if (bullets > 0) {
    		setIndicator(loc, 255, 0, 0);
    		damage += bullets;
    	}
    	
    	if (!overrideDanger) {
	    	Team enemy = rc.getTeam().opponent();
	    	float startDamage = damage;
	    	for (RobotInfo r:robots) {
	    		if (r.getTeam() == enemy && r.getType().bulletSpeed > 0) { //Only care about robots that can shoot
	    			float dist = r.getLocation().distanceTo(loc) - rc.getType().bodyRadius;
	        		float range = r.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET + r.getType().bulletSpeed + r.getType().strideRadius;
	        		if (range >= dist) {
	        			damage += r.getType().attackPower;
	        		}
	    		}
	    	}
	    	if (damage > startDamage)
    			setIndicator(loc, 128, 0, 0);
    	}
    	
    	if (damage == 0)
    		setIndicator(loc, 0, 255, 0);
    	
    	return damage;
    }
    
    /*
     * Wander
     * 
     * Stores the current direction of travel and keeps moving in that direction until we hit something
     * Then try another random direction
     * Since tanks can move onto trees - tryMove could return true but we haven't actually moved so we check
     * and if the tree damaged is one of ours we try another direction
     */
    static Direction wanderDir = randomDirection();
    static boolean wallMode = false;
    static boolean followLeft = true;
    
    static boolean moveTo(MapLocation dest) throws GameActionException {
    	if (wallMode)
    		return wallMove(dest);
    	else
    		return tryMove(dest);
    }
    
    static void wander() throws GameActionException {
    	if (rc.hasMoved())
    		return;
    	
    	debug(2, "Wandering");
    	if (wanderDir == null)
    		wanderDir = randomDirection();

    	if (!tryMove(rc.getLocation().add(wanderDir, rc.getType().strideRadius), 0, 0, 0)) {
        	wanderDir = randomDirection();
        }
    }
    
    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(MapLocation to) throws GameActionException {
        return tryMove(to, 22, 4, 4);
    }
    
    /*
     * Checks to see if we can move here
     * Uses rc.canMove and then performs extra checks for a TANK unit as we don't want to destroy our own trees
     */
    static boolean canMove(MapLocation dest) throws GameActionException {
    	float dist = rc.getLocation().distanceTo(dest);
        if(dist > rc.getType().strideRadius) {
            Direction dir = rc.getLocation().directionTo(dest);
            dest = rc.getLocation().add(dir, rc.getType().strideRadius);
        }
        
    	if (!rc.canMove(dest))
    		return false;
    	if (rc.getType() == RobotType.TANK) {
    		TreeInfo[] bump = rc.senseNearbyTrees(dest, RobotType.TANK.bodyRadius, rc.getTeam());
    		if (bump.length > 0)
    			return false;
    	}
    	
    	return true;
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles in the path and avoiding bullets
     *
     * @param to The intended destination
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(MapLocation to, float degreeOffset, int checksLeft, int checksRight) throws GameActionException {   	
    	if (rc.hasMoved() || to == null)
    		return false;    	
        
    	MapLocation here = rc.getLocation();
    	Direction dir = here.directionTo(to);
    	float dist = here.distanceTo(to); 
    	MapLocation dest = to;
    	
    	if (dir == null || dist <= 0 || here == to)
    		return true;
    	
    	if (dist > rc.getType().strideRadius) {
    		dist = rc.getType().strideRadius;
    		dest = here.add(dir, dist);
    	}
    	
    	MapLocation bestUnsafe = null;
    	float leastDamage = 1000;
    	float damage;
    	
        // First, try intended direction
        if (canMove(dest)) {
        	damage = damageAtLocation(dest);
        	if (damage > 0 && damage < leastDamage) {
        		leastDamage = damage;
        		bestUnsafe = dest;
        	}
        	if (damage == 0) {
        		wanderDir = here.directionTo(dest);
	            rc.move(dest);
	            setIndicator(here, dest, 0, 255, 0);
	            return true;
        	}
        }
    	
        // Now try a bunch of similar angles
        int currentCheck = 1;
        int checksPerSide = Math.max(checksLeft, checksRight);
        
        debug(3, "tryMove: checking " + checksPerSide + " locations (" + checksLeft + " left and " + checksRight + " right)");

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
        	if (currentCheck <= checksLeft) {
	        	dest = here.add(dir.rotateLeftDegrees(degreeOffset*currentCheck), dist);
	        	if (canMove(dest)) {
	        		damage = damageAtLocation(dest);
	            	if (damage > 0 && damage < leastDamage) {
	            		leastDamage = damage;
	            		bestUnsafe = dest;
	            	}
	            	if (damage == 0) {
	            		wanderDir = here.directionTo(dest);
	    	            rc.move(dest);
	    	            setIndicator(here, dest, 0, 255, 0);
	    	            return true;
	            	}
	            }
        	}

            // Try the offset on the right side
        	if (currentCheck <= checksRight) {
	            dest = here.add(dir.rotateRightDegrees(degreeOffset*currentCheck), dist);
	            if (canMove(dest)) {
	            	damage = damageAtLocation(dest);
	            	if (damage > 0 && damage < leastDamage) {
	            		leastDamage = damage;
	            		bestUnsafe = dest;
	            	}
	            	if (damage == 0) {
	            		wanderDir = here.directionTo(dest);
	    	            rc.move(dest);
	    	            setIndicator(here, dest, 0, 255, 0);
	    	            return true;
	            	}
	            }
        	}
            // No move performed, try slightly further
            currentCheck++;
        }
        
        if (bestUnsafe != null && leastDamage <= damageAtLocation(here) && canMove(bestUnsafe)) { //Not safe here so happy to move to another unsafe place
        	wanderDir = here.directionTo(bestUnsafe);
        	rc.move(bestUnsafe);
        	setIndicator(here, bestUnsafe, 255, 0, 0);
        	return true;
        }

        // A move never happened, so return false.
        return false;
    }
    
    static void engageWallMode(MapLocation target) {
    	wallMode = true;
    	followLeft = (wanderDir.radiansBetween(rc.getLocation().directionTo(target)) > 0);
    }
    
    /*
     * WallMove attempts to follow an obstacle until we are around it
     */
    static boolean wallMove(MapLocation dest) throws GameActionException { 	
    	//Find nearest object blocking on the side we are following - a tree, robot or edge of map
    	MapLocation blocker = null;
    	float distance = 0;
    	TreeInfo wallTree = null; // This is the "wall" we are following
    	float treeDist = 20;
    	RobotInfo wallRobot = null;
    	float robotDist = 20;
    	float bestAngle = 10;
    	
		for (TreeInfo t:trees) {
			distance = rc.getLocation().distanceTo(t.getLocation()) - t.getRadius();
			if (distance > treeDist)
				break;
			float angle = wanderDir.radiansBetween(rc.getLocation().directionTo(t.getLocation()));
			if ((followLeft && angle >= 0) || (!followLeft && angle <= 0)) {
				if (distance < treeDist || Math.abs(angle) < bestAngle) {
					treeDist = distance;
					wallTree = t;
					bestAngle = Math.abs(angle);
				}
			}
		}
		
		for (RobotInfo r:robots) {
			distance = rc.getLocation().distanceTo(r.getLocation()) - r.getType().bodyRadius;
			if (distance > robotDist)
				break;
			float angle = wanderDir.radiansBetween(rc.getLocation().directionTo(r.getLocation()));
			if ((followLeft && angle >= 0) || (!followLeft && angle <= 0)) {
				robotDist = distance;
				wallRobot = r;
			}
		}
		
    	if (wallTree != null && wallRobot != null) {
    		//Work out which is nearest
    		if (treeDist < robotDist) { //Tree is nearer
    			blocker = wallTree.getLocation();
    			distance = treeDist;
    			setIndicator(rc.getLocation(), wallTree.getLocation(), 0, 0, 0);
    		} else {
    			blocker = wallRobot.getLocation();
    			distance = robotDist;
    			setIndicator(rc.getLocation(), wallRobot.getLocation(), 0, 0, 0);
    		}
    	} else if (wallTree != null) {
    		blocker = wallTree.getLocation();
			distance = treeDist;
			setIndicator(rc.getLocation(), wallTree.getLocation(), 0, 0, 0);
		} else if (wallRobot != null) {
			blocker = wallRobot.getLocation();
			distance = robotDist;
			setIndicator(rc.getLocation(), wallRobot.getLocation(), 0, 0, 0);
		}

    	//Check to see if the edge of the map is nearer
		Direction dir = Direction.NORTH;
		int count = 0;
		float testDistance = distance;
		if (testDistance > rc.getType().sensorRadius - rc.getType().bodyRadius - GameConstants.BULLET_SPAWN_OFFSET)
			testDistance = rc.getType().sensorRadius - rc.getType().bodyRadius - GameConstants.BULLET_SPAWN_OFFSET;
		while (count < 4) {
    		MapLocation edge = rc.getLocation().add(dir, testDistance);
    		if (!rc.onTheMap(edge, rc.getType().bodyRadius)) {
    			float angle = wanderDir.radiansBetween(dir);
    			if ((followLeft && angle >= 0) || (!followLeft && angle <= 0)) {
	    			blocker = edge;
	    			break;
    			}
    		}
    		dir = dir.rotateLeftDegrees(90);
    		count++;
		}
    	
    	if (blocker != null) {
	    	if (followLeft)
	    		wanderDir = rc.getLocation().directionTo(blocker).rotateRightDegrees(90);
	    	else
	    		wanderDir = rc.getLocation().directionTo(blocker).rotateLeftDegrees(90);
    	}
    	
    	float diff = Math.abs(wanderDir.radiansBetween(rc.getLocation().directionTo(dest)));		
    	setIndicator(rc.getLocation(), rc.getLocation().add(wanderDir, 3), 128, 128, 128);
    	
    	if (diff > Math.PI/8) {
    		if (followLeft) { //Keep the wall on our left
    			MapLocation towards = rc.getLocation().add(wanderDir.rotateLeftDegrees(40), rc.getType().strideRadius);
	    		return tryMove(towards, 20, 0, 14);
    		} else {
    			MapLocation towards = rc.getLocation().add(wanderDir.rotateRightDegrees(40), rc.getType().strideRadius);
	    		return tryMove(towards, 20, 14, 0);
    		}
    	} else {
    		//We can head towards real destination safely
    		wallMode = false;
    		return tryMove(dest);
    	}
    }

    /*
     * Returns the bullet damage at this location
     */
    static float bulletDamage(MapLocation loc) {
    	float damage = 0;
    	
    	for (BulletInfo b:bullets) {
    		//Will the bullet hit us?
    		//Calc nearest point this bullet gets to us
    		float angle = Math.abs(b.getLocation().directionTo(loc).radiansBetween(b.getDir()));
    		if (angle < Math.PI / 2) {
        		float hypot = b.getLocation().distanceTo(loc);
    			float nearest = (float) (hypot * Math.sin(angle));
    			if (nearest <= rc.getType().bodyRadius)
    				damage += b.getDamage();
    		}
    		if (Clock.getBytecodesLeft() < 2000)
    			break;
    	}

    	return damage;
    }
    
    /*
     * Code copied from server to calculate if a bullet will hit an object
     */
    static float calcHitDist(MapLocation bulletStart, MapLocation bulletFinish,
            MapLocation targetCenter, float targetRadius)
    {
		final float minDist = 0;
		final float maxDist = bulletStart.distanceTo(bulletFinish);
		final float distToTarget = bulletStart.distanceTo(targetCenter);
		final Direction toFinish = bulletStart.directionTo(bulletFinish);
		final Direction toTarget = bulletStart.directionTo(targetCenter);
		
		// If toTarget is null, then bullet is on top of centre of unit, distance is zero
		if(toTarget == null || distToTarget <= targetRadius) {
			return 0;
		}
		
		if(toFinish == null) {
			// This should never happen
			throw new RuntimeException("bulletStart and bulletFinish are the same.");
		}
		
		float radiansBetween = toFinish.radiansBetween(toTarget);
		
		//Check if the target intersects with the line made between the bullet points
		float perpDist = (float)Math.abs(distToTarget * Math.sin(radiansBetween));
		if(perpDist > targetRadius){
			return -1;
		}
		
		//Calculate hitDist
		float halfChordDist = (float)Math.sqrt(targetRadius * targetRadius - perpDist * perpDist);
		float hitDist = distToTarget * (float)Math.cos(radiansBetween);
		if(hitDist < 0){
			hitDist += halfChordDist;
			hitDist = hitDist >= 0 ? 0 : hitDist;
		}else{
			hitDist -= halfChordDist;
			hitDist = hitDist < 0 ? 0 : hitDist;
		}
		
		//Check invalid hitDists
		if(hitDist < minDist || hitDist > maxDist){
			return -1;
		}
		return hitDist;
	}

	
}
