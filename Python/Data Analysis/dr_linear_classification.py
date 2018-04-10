import pandas as pd
import numpy as np
from sklearn.linear_model import Perceptron
import matplotlib.pyplot as plt

def dr_getValOnLine(coef, c, X0):
    a = coef[0][0]
    b = coef[0][1]

    X1 = -(a/b)*X0 - (c/b)
    return X1

if __name__ == "__main__":

    df = pd.read_csv('./linear_data.csv')

    X = df[['X0', 'X1']]
    y = df['L']

    clf = Perceptron(n_iter=30, shuffle=True)
    clf.fit(X, y)
    score = clf.score(X, y)

    print score

    y1 = clf.predict([[0.5, 0.2]])
    print y1

    y2 = clf.predict([[0.5, 0.8]])
    print y2

    # create a mesh to plot in
    h = 0.01
    x_min, x_max = X['X0'].min() - 0.05, X['X0'].max() + 0.05
    y_min, y_max = X['X1'].min() - 0.05, X['X1'].max() + 0.05
    xx, yy = np.meshgrid(np.arange(x_min, x_max, h),
                         np.arange(y_min, y_max, h))

    # Plot the decision boundary. For that, we will assign a color to each
    # point in the mesh [x_min, m_max]x[y_min, y_max].
    fig, ax = plt.subplots()
    Z = clf.predict(np.c_[xx.ravel(), yy.ravel()])

    coef = clf.coef_
    c = clf.intercept_

    # Put the result into a color plot
    Z = Z.reshape(xx.shape)
    ax.contourf(xx, yy, Z, cmap=plt.cm.Paired)
    ax.axis('on')

    # Plot also the training points

    L1 = df[df['L'] == -1]
    L2 = df[df['L'] == 1]

    ax.plot(L1['X0'], L1['X1'], 'x', label='Class -1')
    ax.plot(L2['X0'], L2['X1'], 'o', label='Class +1')

    ax.plot([x_min, x_max], [dr_getValOnLine(coef, c[0], x_min), dr_getValOnLine(coef, c[0], x_max)], '-', label='Boundary')

    ax.set_title('Perceptron Boundary')
    ax.legend()
    plt.xlabel("X0")
    plt.ylabel("X1")
    #ax.grid(True)
    plt.show()
