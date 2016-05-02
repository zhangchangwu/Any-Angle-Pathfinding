package algorithms.incrementalvgs;

import grid.GridGraph;
import algorithms.AStarStaticMemory;
import algorithms.priorityqueue.ReusableIndirectHeap;

public class IVGJPS extends AStarStaticMemory {
    private final int[] neighboursdX;
    private final int[] neighboursdY;
    private int neighbourCount;
    
    private static final float APPROXIMATION_RATIO = (float)Math.sqrt(4 - 2*Math.sqrt(2));
    private static final float BUFFER = 0.000001f;
    private final float OCTILE_ELLIPSE_SIZE;
    private final float upperBound;
    private final IVG visibilityGraph;

    public IVGJPS(GridGraph graph, int sx, int sy, int ex, int ey, float existingPathLength, IVG visibilityGraph) {
        super(graph, sx, sy, ex, ey);
        this.upperBound = existingPathLength;
        this.visibilityGraph = visibilityGraph;
        neighboursdX = new int[8];
        neighboursdY = new int[8];
        this.OCTILE_ELLIPSE_SIZE = upperBound * APPROXIMATION_RATIO + BUFFER;
    }


    @Override
    public void computePath() {
        neighbourCount = 0;
        
        int totalSize = (graph.sizeX+1) * (graph.sizeY+1);

        int start = toOneDimIndex(sx, sy);
        //finish = toOneDimIndex(ex, ey);
        
        pq = new ReusableIndirectHeap(totalSize);
        this.initialiseMemory(totalSize, Float.POSITIVE_INFINITY, -1, false);
        
        initialise(start);
        
        while (!pq.isEmpty()) {
            float dist = pq.getMinValue();
            int current = pq.popMinIndex();
            
            if (dist == Float.POSITIVE_INFINITY) {
                maybeSaveSearchSnapshot();
                break;
            }
            setVisited(current, true);

            int x = graph.toTwoDimX(current);
            int y = graph.toTwoDimY(current);
            
            computeNeighbours(current, x, y); // stores neighbours in attribute.

            for (int i=0;i<neighbourCount;++i) {
                int dx = neighboursdX[i];
                int dy = neighboursdY[i];

                int successor = jump(distance(current), x, y, dx, dy);
                if (successor != -1) {
                    tryRelax(current, x, y, successor);
                }
            }
            
            maybeSaveSearchSnapshot();
        }

        maybePostSmooth();
    }

    /**
     * Returns true iff the point x,y is outside of the generated octile ellipse.
     */
    private final boolean outsideEllipse(float parentDistance, int parX, int parY, int x, int y) {
        return parentDistance + graph.octileDistance(x, y, parX, parY) + graph.octileDistance(x, y, ex, ey) > OCTILE_ELLIPSE_SIZE;
        //return !visibilityGraph.isWithinEllipse(x, y);
    }
    
    private final void tryUpdateHeuristic(float parentDistance, int parX, int parY, int x, int y) {
        int index = visibilityGraph.tryGetIndexOf(x,y);
        if (index == -1) return;
        float heuristicValue = (graph.octileDistance(parX, parY, x, y) + parentDistance) / APPROXIMATION_RATIO;
        visibilityGraph.tryUpdateHeuristic(index, heuristicValue);
    }

    private final void tryRelax(int current, int currX, int currY, int destination) {
        if (visited(destination)) return;
        
        int destX = graph.toTwoDimX(destination);
        int destY = graph.toTwoDimY(destination);
        
        if (relax(current, destination, graph.octileDistance(currX, currY, destX, destY), destX, destY)) {
            // If relaxation is done.
            pq.decreaseKey(destination, distance(destination));
        }
    }
    
    private final boolean relax(int u, int v, float weightUV, int currX, int currY) {
        // return true iff relaxation is done.
        
        float newWeight = distance(u) + weightUV;
        //System.out.println("Relax " + s(u) + "->" + s(v) + " = " + newWeight);
        
        if (newWeight < distance(v)) {
            setDistance(v, newWeight);
            setParent(v, u);
            return true;
        }
        return false;
    }
    
    private final int jump(float parentDistance, int x, int y, int dx, int dy) {
        if (dx < 0) {
            if (dy < 0) {
                return jumpDL(parentDistance,x,y,x,y);
            } else if (dy > 0) {
                return jumpUL(parentDistance,x,y,x,y);
            } else {
                return jumpL(parentDistance,x,y,x,y);
            }
        } else if (dx > 0) {
            if (dy < 0) {
                return jumpDR(parentDistance,x,y,x,y);
            } else if (dy > 0) {
                return jumpUR(parentDistance,x,y,x,y);
            } else {
                return jumpR(parentDistance,x,y,x,y);
            }
        } else {
            if (dy < 0) {
                return jumpD(parentDistance,x,y,x,y);
            } else {
                return jumpU(parentDistance,x,y,x,y);
            }
        }   
    }
    
    private final int jumpDL(float parentDistance, int parX, int parY, int x, int y) {
        while(true) {
            x -= 1;
            y -= 1;
            if (graph.isBlocked(x, y)) return -1;
            if (outsideEllipse(parentDistance,parX,parY,x,y)) return -1;
            tryUpdateHeuristic(parentDistance, parX, parY, x, y);
            // diagonal cannot be forced on vertices.
            if (jumpL(parentDistance,parX,parY,x,y) != -1) return toOneDimIndex(x,y);
            if (jumpD(parentDistance,parX,parY,x,y) != -1) return toOneDimIndex(x,y);
        }
    }
    
    private final int jumpDR(float parentDistance, int parX, int parY, int x, int y) {
        while(true) {
            x += 1;
            y -= 1;
            if (graph.isBlocked(x-1, y)) return -1;
            if (outsideEllipse(parentDistance,parX,parY,x,y)) return -1;
            tryUpdateHeuristic(parentDistance, parX, parY, x, y);
            // diagonal cannot be forced on vertices.
            if (jumpD(parentDistance,parX,parY,x,y) != -1) return toOneDimIndex(x,y);
            if (jumpR(parentDistance,parX,parY,x,y) != -1) return toOneDimIndex(x,y);
        }
    }
    
    private final int jumpUL(float parentDistance, int parX, int parY, int x, int y) {
        while(true) {
            x -= 1;
            y += 1;
            if (graph.isBlocked(x, y-1)) return -1;
            if (outsideEllipse(parentDistance,parX,parY,x,y)) return -1;
            tryUpdateHeuristic(parentDistance, parX, parY, x, y);
            // diagonal cannot be forced on vertices.
            if (jumpL(parentDistance,parX,parY,x,y) != -1) return toOneDimIndex(x,y);
            if (jumpU(parentDistance,parX,parY,x,y) != -1) return toOneDimIndex(x,y);
        }
    }
    
    private final int jumpUR(float parentDistance, int parX, int parY, int x, int y) {
        while(true) {
            x += 1;
            y += 1;
            if (graph.isBlocked(x-1, y-1)) return -1;
            if (outsideEllipse(parentDistance,parX,parY,x,y)) return -1;
            tryUpdateHeuristic(parentDistance, parX, parY, x, y);
            // diagonal cannot be forced on vertices.
            if (jumpU(parentDistance,parX,parY,x,y) != -1) return toOneDimIndex(x,y);
            if (jumpR(parentDistance,parX,parY,x,y) != -1) return toOneDimIndex(x,y);
        }
    }
    
    private final int jumpL(float parentDistance, int parX, int parY, int x, int y) {
        while(true) {
            x -= 1;
            if (outsideEllipse(parentDistance,parX,parY,x,y)) return -1;
            tryUpdateHeuristic(parentDistance, parX, parY, x, y);
            if (graph.isBlocked(x, y)) {
                if (graph.isBlocked(x, y-1)) {
                    return -1;
                } else {
                    if (!graph.isBlocked(x-1, y)) return toOneDimIndex(x,y);
                }
            }
            if (graph.isBlocked(x, y-1)) {
                if (!graph.isBlocked(x-1, y-1)) return toOneDimIndex(x,y);
            }
        }
    }
    
    private final int jumpR(float parentDistance, int parX, int parY, int x, int y) {
        while(true) {
            x += 1;
            if (outsideEllipse(parentDistance,parX,parY,x,y)) return -1;
            tryUpdateHeuristic(parentDistance, parX, parY, x, y);
            if (graph.isBlocked(x-1, y)) {
                if (graph.isBlocked(x-1, y-1)) {
                    return -1;
                } else {
                    if (!graph.isBlocked(x, y)) return toOneDimIndex(x,y);
                }
            }
            if (graph.isBlocked(x-1, y-1)) {
                if (!graph.isBlocked(x, y-1)) return toOneDimIndex(x,y);
            }
        }
    }
    
    private final int jumpD(float parentDistance, int parX, int parY, int x, int y) {
        while(true) {
            y -= 1;
            if (outsideEllipse(parentDistance,parX,parY,x,y)) return -1;
            tryUpdateHeuristic(parentDistance, parX, parY, x, y);
            if (graph.isBlocked(x, y)) {
                if (graph.isBlocked(x-1, y)) {
                    return -1;
                } else {
                    if (!graph.isBlocked(x, y-1)) return toOneDimIndex(x,y);
                }
            }
            if (graph.isBlocked(x-1, y)) {
                if (!graph.isBlocked(x-1, y-1)) return toOneDimIndex(x,y);
            }
        }
    }
    
    private final int jumpU(float parentDistance, int parX, int parY, int x, int y) {
        while(true) {
            y += 1;
            if (outsideEllipse(parentDistance,parX,parY,x,y)) return -1;
            tryUpdateHeuristic(parentDistance, parX, parY, x, y);
            if (graph.isBlocked(x, y-1)) {
                if (graph.isBlocked(x-1, y-1)) {
                    return -1;
                } else {
                    if (!graph.isBlocked(x, y)) return toOneDimIndex(x,y);
                }
            }
            if (graph.isBlocked(x-1, y-1)) {
                if (!graph.isBlocked(x-1, y)) return toOneDimIndex(x,y);
            }
        }
    }

    private final void computeNeighbours(int currentIndex, int cx, int cy) {
        neighbourCount = 0;

        int parentIndex = parent(currentIndex);
        if (parentIndex == -1) {
            // is start node.
            for (int y=-1;y<=1;++y) {
                for (int x=-1;x<=1;++x) {
                    if (x == 0 && y == 0) continue;
                    int px = cx+x;
                    int py = cy+y;
                    if (graph.neighbourLineOfSight(cx,cy,px,py)) {
                        addNeighbour(x, y);
                    }
                }
            }
            return;
        }

        int dirX = cx - graph.toTwoDimX(parentIndex);
        int dirY = cy - graph.toTwoDimY(parentIndex);

        if (dirX < 0) {
            if (dirY < 0) {
                // down-left
                if (!graph.isBlocked(cx-1, cy-1)) {
                    addNeighbour(-1,-1);
                    addNeighbour(-1,0);
                    addNeighbour(0,-1);
                } else {
                    if (!graph.isBlocked(cx-1, cy)) addNeighbour(-1,0);
                    if (!graph.isBlocked(cx, cy-1)) addNeighbour(0,-1);
                }
            } else if (dirY > 0) {
                // up-left
                if (!graph.isBlocked(cx-1, cy)) {
                    addNeighbour(-1,1);
                    addNeighbour(-1,0);
                    addNeighbour(0,1);
                } else {
                    if (!graph.isBlocked(cx-1, cy-1)) addNeighbour(-1,0);
                    if (!graph.isBlocked(cx, cy)) addNeighbour(0,1);
                }
            } else {
                // left
                if (graph.isBlocked(cx,cy)) {
                    //assert !graph.isBlocked(cx-1, cy);
                    addNeighbour(-1,1);
                    addNeighbour(0,1);
                    addNeighbour(-1,0);
                } else {
                    //assert graph.isBlocked(cx,cy-1);
                    //assert !graph.isBlocked(cx-1, cy-1);
                    addNeighbour(-1,-1);
                    addNeighbour(0,-1);
                    addNeighbour(-1,0);
                }
            }
        } else if (dirX > 0) {
            if (dirY < 0) {
                // down-right
                if (!graph.isBlocked(cx, cy-1)) {
                    addNeighbour(1,-1);
                    addNeighbour(1,0);
                    addNeighbour(0,-1);
                } else {
                    if (!graph.isBlocked(cx, cy)) addNeighbour(1,0);
                    if (!graph.isBlocked(cx-1, cy-1)) addNeighbour(0,-1);
                }
            } else if (dirY > 0) {
                // up-right
                if (!graph.isBlocked(cx, cy)) {
                    addNeighbour(1,1);
                    addNeighbour(1,0);
                    addNeighbour(0,1);
                } else {
                    if (!graph.isBlocked(cx, cy-1)) addNeighbour(1,0);
                    if (!graph.isBlocked(cx-1, cy)) addNeighbour(0,1);
                }
            } else {
                // right
                if (graph.isBlocked(cx-1,cy)) {
                    //assert !graph.isBlocked(cx, cy);
                    addNeighbour(1,1);
                    addNeighbour(0,1);
                    addNeighbour(1,0);
                } else {
                    //assert graph.isBlocked(cx-1,cy-1);
                    //assert !graph.isBlocked(cx, cy-1);
                    addNeighbour(1,-1);
                    addNeighbour(0,-1);
                    addNeighbour(1,0);
                }
            }
        } else {
            if (dirY < 0) {
                // down
                if (graph.isBlocked(cx,cy)) {
                    //assert !graph.isBlocked(cx, cy-1);
                    addNeighbour(1,-1);
                    addNeighbour(1,0);
                    addNeighbour(0,-1);
                } else {
                    //assert graph.isBlocked(cx-1,cy);
                    //assert !graph.isBlocked(cx-1, cy-1);
                    addNeighbour(-1,-1);
                    addNeighbour(-1,0);
                    addNeighbour(0,-1);
                }
            } else { //dirY > 0
                // up
                if (graph.isBlocked(cx,cy-1)) {
                    //assert !graph.isBlocked(cx, cy);
                    addNeighbour(1,1);
                    addNeighbour(1,0);
                    addNeighbour(0,1);
                } else {
                    //assert graph.isBlocked(cx-1,cy-1);
                    //assert !graph.isBlocked(cx-1, cy);
                    addNeighbour(-1,1);
                    addNeighbour(-1,0);
                    addNeighbour(0,1);
                }
            }
        }
    }
    
    private final void addNeighbour(int x, int y) {
        neighboursdX[neighbourCount] = x;
        neighboursdY[neighbourCount] = y;
        neighbourCount++;
    }
    
}