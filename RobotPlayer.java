package yggdrasil;

import battlecode.common.*;

/*
 * Todo list
 * 
 * Implement a simple pathfinding algorithm to a given location
 * Find a way to make use of broadcast data from the enemy
 * Units that move out of sensor range could be followed
 */
public strictfp class RobotPlayer {
	static final int debugLevel = 0; // 0 = off, 1 = function calls, 2 = logic, 3/4 = detailed info
	static final boolean indicatorsOn = false;
	static final float fireThreshold = 1 + GameConstants.BULLET_TREE_COST; //Ensure we have enough bullets to build a tree
	
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
    
    static void sense() throws GameActionException {
    	robots = rc.senseNearbyRobots();
    	trees = rc.senseNearbyTrees();
    	bullets = rc.senseNearbyBullets();
    	overrideDanger = false;
    	RobotInfo enemy = findNearestRobot(null, rc.getTeam().opponent());
    	if (enemy != null) {
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
    	} else if (bullets > 1000) {   		
    		int newVps = (int)((bullets - 1000)/exchangeRate);
    		rc.donate(newVps*exchangeRate);
    	}
    }
    
    static void help(MapLocation here) throws GameActionException {
    	int round = rc.readBroadcast(0);
    	if (round == rc.getRoundNum()) //Someone else called for help this turn
    		return;
    	
    	rc.broadcast(0, rc.getRoundNum());
    	rc.broadcast(1, (int)here.x);
    	rc.broadcast(2, (int)here.y);
    	debug(4, "Calling for help " + here);
    	setIndicator(here, 0, 0, 255);
    }
    
    static void cancelHelp() throws GameActionException {
    	rc.broadcast(0, 0);
    	rallyPoint = null;
    	debug(4, "Cancelling help");
    }
    
    static void checkHelp() throws GameActionException {
    	int round = rc.readBroadcast(0);
    	if (round > 0) {
    		if (rc.getRoundNum() - round < 50) { //Current help
    			rallyPoint = new MapLocation(rc.readBroadcast(1), rc.readBroadcast(2));
    			if (rc.canSenseLocation(rallyPoint)) {
    				RobotInfo enemy = findNearestRobot(null, rc.getTeam().opponent());
    				if (enemy == null)
    					cancelHelp();
    			}
    		} else {
    			cancelHelp();
    		}
    	} else {
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
     * If it does return the distance to the enemy otherwise return -1
     */
    static float hitDistance(MapLocation loc, Direction dir) {
    	//Check each tree to see if it will hit it - record the nearest tree that we hit
    	float nearestHitTreeDist = rc.getType().bulletSightRadius*2;
    	boolean enemyTree = false;
    	TreeInfo[] trees = rc.senseNearbyTrees(loc, 3, null);
		for (TreeInfo t:trees) {
			float dist = calcHitDist(loc, loc.add(dir, rc.getType().sensorRadius*2), t.getLocation(), t.getRadius());
			if (dist >= 0) {
				if (t.getTeam() == rc.getTeam().opponent())
					enemyTree = true;
				nearestHitTreeDist = dist;
				break;
			}
		}

		RobotInfo nearestUnit = null;
		float nearestHitUnitDist = -1;
		for (RobotInfo r:robots) {
			nearestHitUnitDist = calcHitDist(loc, loc.add(dir, rc.getType().sensorRadius*2), r.getLocation(), r.getRadius());
			if (nearestHitUnitDist >= 0) {
				nearestUnit = r;
				break;
			}
		}
		
		if (nearestUnit != null && nearestHitUnitDist <= nearestHitTreeDist) { //We hit something
			if (nearestUnit.getTeam() != rc.getTeam())
				return nearestHitUnitDist;
			else
				return -1;
		}
		
		if (enemyTree)
			return nearestHitTreeDist;
		
		return -1;
    }
    
    /*
     * The Archon creates new gardeners and acts as the arbiter for what we build
     * It should avoid bullets and can shake trees
     * If it is the last (or only) archon it tries really hard to stay alive
     */
    static void runArchon() throws GameActionException {
        debug(1, "I'm an archon!");
        boolean prime = false;
        
        // The code you want your robot to perform every round should be in this loop
        while (true) {        	
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	checkWin();
            	sense();
            	checkShake();
            	
            	if (rc.getRoundNum() == 1) {
            		if (rc.getTeamBullets() == GameConstants.BULLETS_INITIAL_AMOUNT) {
	            		prime = true;
	            		debug(1, "I am the prime Archon");
	            		tryMove(mapCentre);
            		} else {
            			prime = false;
            		}
            	}
            	
            	RobotInfo nearestGardener = findNearestRobot(RobotType.GARDENER, rc.getTeam());
            	
            	if (((prime && rc.getRoundNum() == 1) || rc.getRoundNum() > 25) && rc.getTeamBullets() > 150 && nearestGardener == null) {
	                Direction dir = rc.getLocation().directionTo(mapCentre);
	                if (dir == null)
	                	dir = randomDirection();

                	for (int i=0; i<20; i++) {
    	                if (rc.canHireGardener(dir)) {
    	                	rc.hireGardener(dir);
    	                    break;
    	                }
            			dir = dir.rotateLeftRads((float)Math.PI*2/20);
            		}	                    
                }
            	
                wander();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                debug(1, "Archon Exception");
                e.printStackTrace();
            }
        }
    }

    static boolean buildIt(RobotType type, Direction dir) throws GameActionException {
    	debug(1, "buildIt: type = " + type + " dir = " + dir);
    	if (dir == null)
    		dir = rc.getLocation().directionTo(mapCentre);
    	
		for (int i=0; i<20; i++) {
			if (rc.canBuildRobot(type, dir)) {
				rc.buildRobot(type, dir);
				return true;
			}
			dir = dir.rotateLeftRads((float)Math.PI*2/20);
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
            	
                //Check to see if we want to build anything
                if (rc.isBuildReady()) {
                	//If there are trees in sight that are not ours - build a lumberjack if there isn't one nearby
	                int lumberjacks = 0;
	                int scouts = 0; // Number of enemy scouts
                	int numTrees = 0;
                	int defenders = 0;
                	for (TreeInfo t:trees) {
                		if (t.getTeam() != me) {
                			numTrees++;
                		}
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
	                
	                if (rc.hasRobotBuildRequirements(RobotType.SCOUT) && !scoutBuilt) {
	                	//Move to centre and build a scout
	                	tryMove(buildLoc);
                		scoutBuilt = buildIt(RobotType.SCOUT, buildDir);
	                }
                
	                if (defenders == 0) {
	                	if (rc.isBuildReady() && rc.hasRobotBuildRequirements(RobotType.TANK)) {
		                	//Move to centre and build a tank
	                		tryMove(buildLoc);
	                		buildIt(RobotType.TANK, buildDir);
	                	}
	                	
	                	if (rc.isBuildReady() && rc.hasRobotBuildRequirements(RobotType.SOLDIER)) {
		                	//Move to centre and build a soldier
	            			tryMove(buildLoc);
	                		buildIt(RobotType.SOLDIER, buildDir);
	                	}
	                }
	                
	                if (rc.hasRobotBuildRequirements(RobotType.LUMBERJACK) && rc.isBuildReady()) {
	                	if (numTrees > lumberjacks || scouts > lumberjacks) {
                			//Move to centre and build
	                		tryMove(buildLoc);
                			buildIt(RobotType.LUMBERJACK, buildDir);
	                	}
	                }
                }
	                
	            //See if we can plant a tree this turn
                //Only do this if we have some defence in place
                if (centre != null) {
	                if (rc.hasTreeBuildRequirements() && !rc.hasMoved()) {
	                	for (int currentSpoke=0; currentSpoke<spokes.length; currentSpoke++) {               
	                		if (plantFrom[currentSpoke] != null && rc.canMove(plantFrom[currentSpoke]) && !rc.isCircleOccupiedExceptByThisRobot(treeCentre[currentSpoke], GameConstants.BULLET_TREE_RADIUS)) {
	                			rc.move(plantFrom[currentSpoke]);
	                			if (rc.getLocation() == plantFrom[currentSpoke] && rc.canPlantTree(spokes[currentSpoke])) {
	                				rc.plantTree(spokes[currentSpoke]);
	                			}
	                			break;
	                		}
		                }
	                }
                }
                
            	if (!rc.hasMoved() && centre != null)
            		tryMove(centre);
                
                if (rc.canWater()) {
	                TreeInfo waterMe = null;
	                for (TreeInfo t:trees) { //Find tree most in need of water  
                		if (rc.canWater(t.getID()) && (waterMe == null || (t.getHealth() < waterMe.getHealth())))
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
	 * shoot works out the optimum fire pattern based on the size of the target and its distance from us then shoots
	 * avoiding friendly fire
	 * 
	 * If the enemy is really close we may have a guaranteed hit of one or more bullets depending on its stride
	 * If it is further away we may need to fire multiple shots to guarantee hitting it with one bullet
	 */
	static void shoot(RobotInfo target) throws GameActionException {
		debug(1, "Shooting at " + target);
		
		float ammo = rc.getTeamBullets();
		
		if (target == null || rc.getAttackCount() > 0 || ammo < fireThreshold || (target.getType() == RobotType.ARCHON && ammo < 500))
			return;
		
		MapLocation targetLoc = target.getLocation();
		MapLocation myLocation = rc.getLocation();
    	Direction dir = myLocation.directionTo(targetLoc);
    	float dist = myLocation.distanceTo(targetLoc);   	
    	float hitDist = hitDistance(myLocation.add(dir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET), dir);
    	
    	if (hitDist < 0) //Bad shot
    		return;
    	
    	//Look at the distance to target and its size to determine if it can dodge
    	//Pentad fires 5 bullets with 15 degrees between each one (spread originating from the centre of the robot firing)
    	//Triad fires 3 bullets with 20 degrees between each one
    	//We can work out the angle either side of the centre of the target at which we hit
    	int roundsToHit = (int) (hitDist / rc.getType().bulletSpeed); // 0 means we hit this round, anything higher gives him chance to dodge
    	float spreadAngle = (float) Math.asin(target.getType().bodyRadius/dist);
    	int shotsToFire = 0;
    	Direction shotDir = dir;
    	debug(3, "shoot: target " + target +  " dist=" + dist + " roundsToHit=" + roundsToHit + " spreadAngle = " + spreadAngle + " (" + Math.toDegrees((double)spreadAngle) + ")");
    	if (rc.canFirePentadShot() && 2*spreadAngle >= Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES*4)) { //All 5 shots will hit
    		shotsToFire = 5;
    		debug (3, "Firing 5 - all should hit");
    	} else if (rc.canFirePentadShot() && 2*spreadAngle >= Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES*3)) { //4 shots will hit
    		shotsToFire = 5;
    		shotDir.rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES/2);
    		debug (3, "Firing 5 - 4 should hit");
    	} else if (rc.canFireTriadShot() && 2*spreadAngle >= Math.toRadians(GameConstants.TRIAD_SPREAD_DEGREES*2)) { //All 3 triad shots will hit
    		shotsToFire = 3;
    		debug (3, "Firing 3 - all should hit");
    	} else if (rc.canFirePentadShot() && 2*spreadAngle >= Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES*2)) { //3 of 5 shots will hit)
    		shotsToFire = 5;
    		debug (3, "Firing 5 - 3 should hit");
    	} else if (rc.canFireTriadShot() && 2*spreadAngle >= Math.toRadians(GameConstants.TRIAD_SPREAD_DEGREES*2)) { //2 of a triad shots will hit
    		shotsToFire = 3;
    		shotDir.rotateLeftDegrees(GameConstants.TRIAD_SPREAD_DEGREES/2);
    		debug (3, "Firing 3 - 2 should hit");
    	} else if (rc.canFirePentadShot() && 2*spreadAngle >= Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES)) { //2 of 5 shots will hit)
    		shotsToFire = 5;
    		shotDir.rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES/2);
    		debug (3, "Firing 5 - 2 should hit");
    	} else if (rc.canFireSingleShot() && roundsToHit <= target.getType().bodyRadius / target.getType().strideRadius) {
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
    		debug(2, "Not shooting at " + target + " hitDist=" + hitDist + " dist=" + dist);
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
                		if (!tryMove(rallyPoint)) //We failed to move so wander for a while
                			turnsToWander = 10;
	                	else if (rc.getType() == RobotType.TANK && rc.getLocation() == here) {
	                    	if (trees.length > 0 && trees[0].getTeam() == rc.getTeam())
	                    		turnsToWander = 10;
	                    }
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
     * That means there has to be room and there is no attacking robot with its stride distance since a shot fired adjacent to a tree could hit us in the tree
     */
    static boolean isTreeSafe(TreeInfo t) throws GameActionException {
    	RobotInfo[] near = rc.senseNearbyRobots(t.getLocation(), t.getRadius()+3, null);
    	for (RobotInfo r: near) {
    		if (r.getLocation().distanceTo(t.getLocation()) < t.getRadius() + r.getType().bodyRadius) { //Occupied
    			setIndicator(t.getLocation(),255,128,128);
    			return false;
    		}
    		if (r.getTeam() != rc.getTeam() && r.getType().canAttack()) { //Enemy
    			float dangerDist = r.getType().strideRadius;
    			if (r.getType() == RobotType.LUMBERJACK)
    				dangerDist += GameConstants.LUMBERJACK_STRIKE_RADIUS;
    			if (r.getLocation().distanceTo(t.getLocation()) <= t.getRadius() + dangerDist) {
    				setIndicator(t.getLocation(), 255,0,0);
    				return false;
    			}
    		}
    	}
    	setIndicator(t.getLocation(),0,255,0);
    	return true;
    }
    
    static boolean canDamage() {
    	if (!rc.getType().canAttack())
    		return false;
    	if (rc.getType() == RobotType.LUMBERJACK)
    		return true;
    	return (rc.getTeamBullets() > fireThreshold);
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

    	MapLocation combatPosition = null; 
    	
        // If there is a threat ...
        if (nearestDanger != null) {
        	MapLocation dangerLoc = nearestDanger.getLocation();
        	
            //If we are a scout then find the nearest available tree to shoot from
        	//Must be close to target and us and safe
            TreeInfo nearestTree = null;
            
            if (rc.getType() == RobotType.SCOUT) {
            	TreeInfo[] trees = rc.senseNearbyTrees(dangerLoc, -1, null);
	            for (TreeInfo t:trees) {
	            	if (t.radius >= RobotType.SCOUT.bodyRadius && isTreeSafe(t)) {
	            		nearestTree = t;
	            		break;
	            	}
	            }
            }
                         	               	
        	if (nearestDanger.getType() == RobotType.LUMBERJACK || (nearestLumberjack != null && myLocation.distanceTo(nearestLumberjack.getLocation()) < safeDistance)) {
        		debug(2, "Keeping at range from enemy lumberjack" + nearestLumberjack);
            	combatPosition = nearestLumberjack.getLocation().add(nearestLumberjack.getLocation().directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
        	} else if (nearestTree != null) { //Scouts can hide in trees
            	float dist = nearestTree.radius-RobotType.SCOUT.bodyRadius - GameConstants.BULLET_SPAWN_OFFSET / 2;
            	debug(2, "Hiding in tree " + nearestTree + " target = " + nearestDanger);
            	if (dist >= 0)
            		combatPosition = nearestTree.getLocation().add(nearestTree.getLocation().directionTo(dangerLoc),dist);
            	else
            		combatPosition = nearestTree.getLocation().add(nearestTree.getLocation().directionTo(dangerLoc).opposite(),-dist);	                		
        	} else if (canBeat(nearestDanger) && canDamage()) {
        		debug(2, "Can Win: Closing on " + nearestDanger);
        		overrideDanger = true;
        		safeDistance = rc.getType().bodyRadius + nearestDanger.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET;
        		if (rc.getType() == RobotType.SCOUT)
        			safeDistance += rc.getType().bulletSpeed; //Since we can only fire 1 bullet no point getting closer
        		
        		combatPosition = dangerLoc.add(dangerLoc.directionTo(myLocation).rotateLeftDegrees(5), safeDistance);
        	} else {
            	debug(2, "Running from " + nearestDanger);
            	RobotType t = nearestDanger.getType();
            	safeDistance = t.strideRadius + t.bulletSpeed + t.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET + GameConstants.BULLET_SPAWN_OFFSET/2;
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
    	if (!enemy.getType().canAttack())
    		return true;
    	if (!rc.getType().canAttack())
    		return false;

    	return (rc.getHealth() * rc.getType().attackPower >= enemy.getHealth() * enemy.getType().attackPower);
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
	                	tryMove(archons[(archon_to_visit+nearest)%archons.length]);
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
    	float dist = rc.getType().bodyRadius + target.getType().bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS / 2;
    	
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
            	
            	// See if there are any enemy robots within striking range
                RobotInfo[] local = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS+RobotType.LUMBERJACK.strideRadius, enemy);   
                if (local.length > 0) {
                	RobotInfo target = local[0];
                	if (target.getLocation().distanceTo(rc.getLocation()) >= RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS) {
                		overrideDanger = canBeat(target);
                		moveTowards(target);
                	}
                }
                
                local = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
                if (local.length > 0) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
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
	            		} else { //Move to best to chop something if we can
	            			Direction dir = rc.getLocation().directionTo(best.getLocation());
	            			float dist = rc.getLocation().distanceTo(best.getLocation()) - best.getRadius() - RobotType.LUMBERJACK.bodyRadius;
	            			MapLocation chopLoc = rc.getLocation().add(dir, dist);
	            			tryMove(chopLoc);
	            			
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
	            		}
	            	}
            	}
            	
                if (!rc.hasMoved()) {
                    // No close robots, so search for robots within sight radius
                    RobotInfo target = findNearestRobot(null, enemy);

                    // If there is a robot, move towards it
                    if(target != null) {
                        moveTowards(target);
                    } else if (!chopped) {
                        wander();
                    }
                    
                    if (!rc.hasAttacked()) {
	                    robots = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
	                    if (robots.length > 0) {
	                        // Use strike() to hit all nearby robots!
	                        rc.strike();
	                    }
                    }
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
     * isLocationSafe
     * 
     * Works out if we will be shot at the given location
     * or if a lumberjack is in range (ours or the enemies!)
     * or if an enemy going after us can fire and hit us this turn
     * 
     * If we are a scout and the location is in a tree we are safe from bullets
     * 
     * Returns amount of damage we would take at this location
     */
    static float isLocationSafe(MapLocation loc) throws GameActionException {
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
  	
    	if (!overrideDanger) {
	    	Team enemy = rc.getTeam().opponent();
	    	for (RobotInfo r:robots) {
	    		if (r.getTeam() == enemy && r.getType().bulletSpeed > 0) { //Only care about robots that can shoot
	    			float dist = r.getLocation().distanceTo(loc) - rc.getType().bodyRadius;
	        		float range = r.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET + r.getType().bulletSpeed + r.getType().strideRadius;
	        		if (range >= dist) {
	        			setIndicator(loc, 128, 0, 0);
	        			damage += r.getType().attackPower;
	        		}
	    		}
	    	}
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
    static Direction wanderDir = null;
    
    static void wander() throws GameActionException {
    	if (wanderDir == null)
    		wanderDir = randomDirection();
    	
    	if (rc.hasMoved())
    		return;
    	
    	debug(2, "Wandering");
    	MapLocation here = rc.getLocation();
    	if (!tryMove(rc.getLocation().add(wanderDir, rc.getType().strideRadius), 0, 0)) {
        	wanderDir = randomDirection();
        } else if (rc.getType() == RobotType.TANK && rc.getLocation() == here) {
        	if (trees.length > 0 && trees[0].getTeam() == rc.getTeam())
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
        return tryMove(to,20,5);
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
    static boolean tryMove(MapLocation to, float degreeOffset, int checksPerSide) throws GameActionException {	
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
        if (rc.canMove(dest)) {
        	damage = isLocationSafe(dest);
        	if (damage > 0 && damage < leastDamage) {
        		leastDamage = damage;
        		bestUnsafe = dest;
        	}
        	if (damage == 0) {
	            rc.move(dest);
	            setIndicator(here, dest, 0, 255, 0);
	            return true;
        	}
        }
    	
        // Now try a bunch of similar angles
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
        	dest = here.add(dir.rotateLeftDegrees(degreeOffset*currentCheck), dist);
        	if (rc.canMove(dest)) {
        		damage = isLocationSafe(dest);
            	if (damage > 0 && damage < leastDamage) {
            		leastDamage = damage;
            		bestUnsafe = dest;
            	}
            	if (damage == 0) {
    	            rc.move(dest);
    	            setIndicator(here, dest, 0, 255, 0);
    	            return true;
            	}
            }

            // Try the offset on the right side
            dest = here.add(dir.rotateRightDegrees(degreeOffset*currentCheck), dist);
            if (rc.canMove(dest)) {
            	damage = isLocationSafe(dest);
            	if (damage > 0 && damage < leastDamage) {
            		leastDamage = damage;
            		bestUnsafe = dest;
            	}
            	if (damage == 0) {
    	            rc.move(dest);
    	            setIndicator(here, dest, 0, 255, 0);
    	            return true;
            	}
            }
            // No move performed, try slightly further
            currentCheck++;
        }
        
        if (bestUnsafe != null && leastDamage <= isLocationSafe(here) && rc.canMove(bestUnsafe)) { //Not safe here so happy to move to another unsafe place
        	rc.move(bestUnsafe);
        	setIndicator(here, dest, 255, 0, 0);
        	return true;
        }

        // A move never happened, so return false.
        return false;
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
