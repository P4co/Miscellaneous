import operator

def dfs_paths(graph, start, goal, path=None):
    if path is None:
        path = [start]
    if start == goal:
        yield path
    for next in graph[start] - set(path):
        yield from dfs_paths(graph, next, goal, path + [next])

def bfs_paths(graph, start, goal):
    queue = [(start, [start])]
    while queue:
        (vertex, path) = queue.pop(0)
        for next in graph[vertex] - set(path):
            if next == goal:
                yield path + [next]
            else:
                queue.append((next, path + [next]))

graph = {'A': set(['B', 'C']),
         'B': set(['A', 'D', 'E']),
         'C': set(['A', 'F']),
         'D': set(['B']),
         'E': set(['B', 'F']),
         'F': set(['C', 'E'])}


'''
graph = {'A': set(['B', 'C', 'E']),
         'B': set(['A', 'C', 'D']),
         'C': set(['D','F']),
         'D': set(['C']),
         'E': set(['F', 'D']),
         'F': set(['C'])}
'''

valuesDFS = list(dfs_paths(graph, 'A', 'F'))
print ('DFS is:',valuesDFS)
minimunpath = min(valuesDFS, key=len)
print ('The minimun path by DFS is',minimunpath)


valuesBFSpaths = list(bfs_paths(graph, 'A', 'F'))
print ('BFS path',valuesBFSpaths)
minimunpath = min(valuesDFS, key=len)
print ('The minimun path by BFS is',minimunpath)
