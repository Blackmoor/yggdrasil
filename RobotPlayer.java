package yggdrasil;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.*;

public strictfp class RobotPlayer {
	static final int debugLevel = 0; // 0 = off, 1 = function calls, 2 = logic, 3/4 = detailed info
	static final boolean indicatorsOn = false;
	
    static RobotController rc;
    static MapLocation mapCentre;
    static MapLocation bottomLeft;
    static MapLocation topRight;
    static RobotInfo[] robots; //Cached result from senseNearbyRobots
    static TreeInfo[] trees; //Cached result from senseNearbyTree
    static BulletInfo[] bullets; //Cached result from senseNearbyBullets
    static boolean overrideDanger = false;

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
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case SCOUT:
                runScout();
                break;
            case TANK:
                runSoldier();
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
    }
    
    static void checkWin() throws GameActionException {
    	// Go for the win if we have enough bullets
    	int vps = rc.getTeamVictoryPoints();
    	float bullets = rc.getTeamBullets();
    	if (rc.getRoundNum() >= rc.getRoundLimit() -1 || (int)(bullets/GameConstants.BULLET_EXCHANGE_RATE) + vps >= GameConstants.VICTORY_POINTS_TO_WIN) {
    		rc.donate(bullets);
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
    
    /*
     * Broadcast data layout
     * 0 - 40*TopRight x coord off map
     * 1 - 40*TopRight x coord off map
     * 2 - 40*BottomLeft x coord off map
     * 3 - 40*BottomRight y coord off map
     */

    static void getMapEdges() throws GameActionException {
    	//Stores the current best known map edges in the globals topLeft and bottomRight
    	float trX = (float)rc.readBroadcast(0) / 40;
    	float trY = (float)rc.readBroadcast(1) / 40;
    	float blX = (float)rc.readBroadcast(2) / 40;
    	float blY = (float)rc.readBroadcast(3) / 40;
    	
    	if (trX == 0)
    		trX = 800;
    	if (trY == 0)
    		trY = 800;

    	topRight = new MapLocation(trX, trY);
    	bottomLeft = new MapLocation(blX, blY);
    }
    
    static void updateMapEdges() throws GameActionException {
    	getMapEdges();

    	MapLocation me = rc.getLocation();
    	MapLocation sense = me.add(Direction.getEast(), rc.getType().sensorRadius - 0.01f);
    	if (!rc.onTheMap(sense)) {
    		if (sense.x < topRight.x)
    			rc.broadcast(0, (int)(sense.x*40));
    	}
    	sense = me.add(Direction.getNorth(), rc.getType().sensorRadius - 0.01f);
    	if (!rc.onTheMap(sense)) {
    		if (sense.y < topRight.y)
    			rc.broadcast(1, (int)(sense.y*40));
    	}
    	sense = me.add(Direction.getWest(), rc.getType().sensorRadius - 0.01f);
    	if (!rc.onTheMap(sense)) {
    		if (sense.x > bottomLeft.x)
    			rc.broadcast(2, (int)(sense.x*40));
    	}
    	sense = me.add(Direction.getSouth(), rc.getType().sensorRadius - 0.01f);
    	if (!rc.onTheMap(sense)) {
    		if (sense.y > bottomLeft.y)
    			rc.broadcast(3, (int)(sense.y*40));
    	}
    }
    
    static boolean closeToMapEdge(float dist) {
    	return (topRight.y - rc.getLocation().y < dist ||
        		topRight.x - rc.getLocation().x < dist ||
        		rc.getLocation().x - bottomLeft.x < dist ||
        		rc.getLocation().y - bottomLeft.y < dist);
    }
    
    //Check for edges of the map - too close and we don't want to build
    static boolean isGoodGardenLocation() throws GameActionException {
    	return (!closeToMapEdge(6) && findNearestRobot(RobotType.GARDENER, rc.getTeam()) == null);
    }

    /*
     * Check to see if a bullet fired from here will hit an enemy first (rather than a tree or an ally)
     * If it does return the distance to the enemy otherwise return -1
     */
    static float hitDistance(MapLocation loc, Direction dir) {
    	//Check each tree to see if it will hit it - record the nearest tree that we hit
    	float nearestHitTreeDist = rc.getType().bulletSightRadius*2;
    	TreeInfo[] trees = rc.senseNearbyTrees(loc, 3, null);
		for (TreeInfo t:trees) {
			float dist = calcHitDist(loc, loc.add(dir, rc.getType().sensorRadius*2), t.getLocation(), t.getRadius());
			if (dist >= 0) {
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
		
		if (nearestUnit != null && nearestUnit.getTeam() != rc.getTeam() && nearestHitUnitDist <= nearestHitTreeDist)
			return nearestHitUnitDist;
		
		return -1;
    }
    
    /*
     * Dodge
     * 
     * Dodge incoming bullets by moving away from the bullet but checking lots of options until we find a safe one
     */
    static void dodge() throws GameActionException {
    	if (isLocationSafe(rc.getLocation()) > 0) {
    		MapLocation moveTo;
    	
	    	BulletInfo[] hitBy = namedBullets(rc.getLocation());
	    	if (hitBy.length == 0)
	    		moveTo = rc.getLocation().add(randomDirection());
	    	else
	    		moveTo = hitBy[0].getLocation();
	   	
	    	tryMove(rc.getLocation().add(rc.getLocation().directionTo(moveTo), rc.getType().strideRadius), 18, 10);
    	}
    }
    
    /*
     * The Archon creates new gardeners and acts as the arbiter for what we build
     * It should avoid bullets and can shake trees
     * If it is the last (or only) archon it tries really hard to stay alive
     */
    static void runArchon() throws GameActionException {
        debug(1, "I'm an archon!");
        boolean hired = false;
        
        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	checkWin();
            	updateMapEdges();
            	sense();
            	checkShake();
            	
                // Build a gardener if there are none in sight and we have room to build a garden
                if (isGoodGardenLocation() || (!hired && rc.getRoundNum() > 10)) {
	                Direction buildDir = rc.getLocation().directionTo(mapCentre);
	                if (rc.canHireGardener(buildDir)) {
	                    rc.hireGardener(buildDir);
	                    hired = true;
	                }
                }

                dodge();
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
    	boolean planted = false;
    	boolean movedAway = false;
    	MapLocation centre = null;
    	Direction spokes[] = new Direction[sides-2];
        MapLocation plantFrom[] = new MapLocation[sides-2];
        MapLocation treeCentre[] = new MapLocation[sides-2];
        Direction buildDir = null;
    	
        // The code you want your robot to perform every round should be in this loop
        while (true) {
        	
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	checkWin();
            	updateMapEdges();           	
                sense();
            	checkShake();
            	dodge();
            	
            	if (centre == null && isGoodGardenLocation()) {
	            	//We centre ourselves here and build a circle of trees around us on the points of a 9 sided shape with 2 trees missing to leave space to move
	                //This shape allows us to water any tree from the centre without moving
            		centre = rc.getLocation();
	                buildDir = centre.directionTo(mapCentre).opposite();       
	                
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
            	} else {
	                //Check to see if we want to build anything
	                if (rc.isBuildReady()) {
	                	//If there are trees in sight that are not ours - build a lumberjack if there isn't one nearby
		                int lumberjacks = 0;
		                int enemies = 0;
		                int allies = 0;
	                	int numTrees = 0;
	                	for (TreeInfo t:trees) {
	                		if (t.getTeam() != me) {
	                			numTrees++;
	                		}
	                	}
		                
		                for (RobotInfo r: robots) {
		                	if (r.getTeam() != me) {
		                		enemies++;
		                	} else if (r.getType() == RobotType.LUMBERJACK)
		                		lumberjacks++;
		                	else if (r.getType().canAttack())
		                		allies++;
		                }
		                
		                if (rc.hasRobotBuildRequirements(RobotType.SCOUT) && !scoutBuilt) {
		                	//Move to centre and build a scout
	            			tryMove(centre);
	                		scoutBuilt = buildIt(RobotType.SCOUT, buildDir);
		                }
		                
		                if (rc.hasRobotBuildRequirements(RobotType.LUMBERJACK) && rc.isBuildReady()) {
		                	if (numTrees > lumberjacks) {
	                			//Move to centre and build
	                			tryMove(centre);
	                			buildIt(RobotType.LUMBERJACK, buildDir);
		                	}
		                }
	                
		                if (rc.isBuildReady() && enemies > 0 && enemies >= allies) {
		                	if (rc.hasRobotBuildRequirements(RobotType.TANK)) {
			                	//Move to centre and build a soldier
		            			tryMove(centre);
		                		buildIt(RobotType.TANK, buildDir);
		                	} else if (rc.hasRobotBuildRequirements(RobotType.SOLDIER)) {
			                	//Move to centre and build a soldier
		            			tryMove(centre);
		                		buildIt(RobotType.SOLDIER, buildDir);
		                	}
		                }
	                }
	                
	                //See if we can plant a tree this turn
	                //To ensure we have some defence wait we have at least 100 bullets so ensure the build orders above kicked in
	                if (rc.hasTreeBuildRequirements() && (!planted || rc.getTeamBullets() > 100) && !rc.hasMoved() && rc.getLocation().distanceTo(centre) <= RobotType.GARDENER.strideRadius + RobotType.GARDENER.bodyRadius) {
	                	for (int currentSpoke=0; currentSpoke<spokes.length; currentSpoke++) {               
	                		if (rc.canMove(plantFrom[currentSpoke]) && !rc.isCircleOccupiedExceptByThisRobot(treeCentre[currentSpoke], GameConstants.BULLET_TREE_RADIUS)) {
	                			rc.move(plantFrom[currentSpoke]);
	                			if (rc.getLocation() == plantFrom[currentSpoke] && rc.canPlantTree(spokes[currentSpoke])) {
	                				rc.plantTree(spokes[currentSpoke]);
	                				planted = true;
	                			}
	                			break;
	                		}
		                }
	                }
	                
	            	if (!rc.hasMoved())
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
	 */
	static void shoot(RobotInfo target) throws GameActionException {
		debug(1, "Shooting at " + target);
		
		float ammo = rc.getTeamBullets();
		
		if (target == null || rc.getAttackCount() > 0 || ammo <= GameConstants.BULLET_TREE_COST || (target.getType() == RobotType.ARCHON && ammo < 500))
			return;
		
		MapLocation targetLoc = target.getLocation();
		MapLocation myLocation = rc.getLocation();
    	Direction dir = myLocation.directionTo(targetLoc);
    	float dist = myLocation.distanceTo(targetLoc);   	
    	float hitDist = hitDistance(myLocation.add(dir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET), dir);
    	
    	if (hitDist < 0) //Bad shot
    		return;
    	
    	float pentad1Hit = (float) (target.getType().bodyRadius / Math.sin(Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES/2)));
    	float pentadAllHit = (float) (target.getType().bodyRadius / Math.sin(Math.toRadians(GameConstants.PENTAD_SPREAD_DEGREES*2)));
    	float triad1Hit = (float) (target.getType().bodyRadius / Math.sin(Math.toRadians(GameConstants.TRIAD_SPREAD_DEGREES/2)));
    	float triadAllHit = (float) (target.getType().bodyRadius / Math.sin(Math.toRadians(GameConstants.TRIAD_SPREAD_DEGREES)));
		
    	if (rc.canFirePentadShot() && dist <= pentad1Hit && dist >= pentadAllHit) {
    		rc.firePentadShot(dir);
    		debug(2, "Shooting 5 shots at " + target);
    	} else if (rc.canFireTriadShot() && dist <= triad1Hit && dist >= triadAllHit) {
    		rc.fireTriadShot(dir);
    		debug(2, "Shooting 3 shots at " + target);
    	} else if (rc.canFireSingleShot()) {
    		int roundsBeforeWeHit = (int)(hitDist / rc.getType().bulletSpeed);

    		if (target.getType() == RobotType.LUMBERJACK) //These want to close on us so we fire as they may not dodge
    			roundsBeforeWeHit--;
    		
    		if (roundsBeforeWeHit <= target.getType().bodyRadius / target.getType().strideRadius) {
    			rc.fireSingleShot(dir);
    			debug(2, "Shooting 1 shot at " + target);
    		}
		}
    	if (rc.getAttackCount() > 0) { //We shot so update bullet info
    		bullets = rc.senseNearbyBullets();
    	} else {
    		debug(2, "Not shooting at " + target + " hitDist=" + hitDist + " dist=" + dist);
    	}
	}
	
    static void runSoldier() throws GameActionException {
        debug(1, "I'm a soldier!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {       	
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	checkWin();
            	updateMapEdges();
            	sense();
            	checkShake();
                boolean stay = manageCombat();
                dodge();
                if (!stay) {
                	wander();
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                debug(1, "Soldier Exception");
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
    		if (r.getType() == RobotType.LUMBERJACK && r.getID() != rc.getID() && loc.distanceTo(r.getLocation()) <= lumberjackRange()) {
    			if (r.getTeam() != rc.getTeam())
    				enemyLumberjacks++;
    			else
    				allyLumberjacks++;
    		}
    	}
    	
    	if (enemies > 0)
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
        	} else if (canBeat(nearestDanger)) {
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
            	updateMapEdges();
            	sense();
            	checkShake();
                boolean stay = manageCombat();
                // Move towards current enemy archon position
                if (!stay && !rc.hasMoved()) {
                	dodge();
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
    	Direction dir = here.directionTo(target.getLocation());
    	float dist = here.distanceTo(target.getLocation()) - rc.getType().bodyRadius - target.getType().bodyRadius;
    	
    	tryMove(here.add(dir, dist));
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
            	updateMapEdges();
            	sense();
            	checkShake();
            	
            	// See if there are any enemy robots within striking range
                RobotInfo[] local = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS+RobotType.LUMBERJACK.strideRadius, enemy);   
                if (local.length > 0) {
                	RobotInfo target = local[0];
                	if (target.getLocation().distanceTo(rc.getLocation()) > RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS)
                		moveTowards(target);
                }
                
                local = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
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
	            			tryMove(best.getLocation());
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
                	dodge();
                    // No close robots, so search for robots within sight radius
                    RobotInfo target = findNearestRobot(null, enemy);

                    // If there is a robot, move towards it
                    if(target != null) {
                        tryMove(target.getLocation());
                    } else if (!chopped) {
                        wander();
                    }
                    
                    if (!rc.hasAttacked()) {
	                    robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
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
    
    /*
     * Returns the bullets with our name on it (the one that will hit us!)
     */
    static BulletInfo[] namedBullets(MapLocation loc) {
    	List<BulletInfo> results = new ArrayList<BulletInfo>();
    	
    	for (BulletInfo b:bullets) {
    		//Will the bullet hit us?
    		MapLocation end = b.getLocation().add(b.getDir(), rc.getType().bulletSightRadius*2);
    		if (calcHitDist(b.getLocation(), end, loc, rc.getType().bodyRadius) >= 0)
    			results.add(b);
    	}
    	
    	return results.toArray(new BulletInfo[results.size()]);
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
        
        if (!overrideDanger) {
	        int lumberjacks = lumberjacksInRange(loc);
	    	if (lumberjacks > 0) {
	    		setIndicator(loc, 255, 0, 128);
	    		damage += lumberjacks * RobotType.LUMBERJACK.attackPower;
	    	}
        }
    	
    	if (rc.getType() == RobotType.SCOUT) {
    		TreeInfo tree = getTreeAtLocation(loc);
    		if (tree != null && withinTree(tree) && isTreeSafe(tree)) {
	    		setIndicator(loc, 0, 255, 128);
	    		return damage;
    		}
    	}
    	
    	BulletInfo[] hitBy = namedBullets(loc);
    	if (hitBy.length > 0) {
    		setIndicator(loc, 255, 0, 0);
    		for (BulletInfo b: hitBy)
    			damage += b.getDamage();
    	}
  	
    	if (!overrideDanger) {
	    	Team enemy = rc.getTeam().opponent();
	    	for (RobotInfo r:robots) {
	    		if (r.getTeam() == enemy && /*rc.getID() < r.getID() &&*/ r.getType().bulletSpeed > 0) { //Only care about robots that can shoot
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
        return tryMove(to,30,3);
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
    	if (rc.hasMoved())
    		return false;    	
           	
    	MapLocation here = rc.getLocation();
    	Direction dir = here.directionTo(to);
    	float dist = here.distanceTo(to); 
    	MapLocation dest = to;
    	
    	if (dir == null || dist <= 0)
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
        
        if (bestUnsafe != null && leastDamage <= isLocationSafe(here)) { //Not safe here so happy to move to another unsafe place
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
