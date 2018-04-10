import pandas as pd
from mpl_toolkits.mplot3d import Axes3D
import matplotlib.pyplot as plt

if __name__ == "__main__":

    iris_data = pd.read_csv('./iris_data.csv', encoding='utf-8')

    iris_data_L1 = iris_data[iris_data['type']==1]
    iris_data_L2 = iris_data[iris_data['type']==2]
    iris_data_L3 = iris_data[iris_data['type']==3]

    fig = plt.figure()
    ax = Axes3D(fig)
    #SepalLength, SepalWidth, PetalLength, PetalWidth
    l1 = ax.scatter(iris_data_L1['SepalLength'], iris_data_L1['SepalWidth'], iris_data_L1['PetalLength'], marker='x')
    l2 = ax.scatter(iris_data_L2['SepalLength'], iris_data_L2['SepalWidth'], iris_data_L2['PetalLength'], marker='o')
    l3 = ax.scatter(iris_data_L3['SepalLength'], iris_data_L3['SepalWidth'], iris_data_L3['PetalLength'], marker='+')

    ax.set_xlabel('SepalLength')
    ax.set_ylabel('SepalWidth')
    ax.set_zlabel('PetalLength')

    plt.legend((l1, l2, l3),
               (iris_data_L1.Name.iloc[1], iris_data_L2.Name.iloc[1], iris_data_L3.Name.iloc[1]),
               scatterpoints=1,
               loc='lower left',
               ncol=3,
               fontsize=8)
    plt.show()

    fig = plt.figure()
    ax = Axes3D(fig)
    # SepalLength, SepalWidth, PetalLength, PetalWidth
    l1 = ax.scatter(iris_data_L1['SepalLength'], iris_data_L1['SepalWidth'], iris_data_L1['PetalWidth'], marker='x')
    l2 = ax.scatter(iris_data_L2['SepalLength'], iris_data_L2['SepalWidth'], iris_data_L2['PetalWidth'], marker='o')
    l3 = ax.scatter(iris_data_L3['SepalLength'], iris_data_L3['SepalWidth'], iris_data_L3['PetalWidth'], marker='+')

    ax.set_xlabel('SepalLength')
    ax.set_ylabel('SepalWidth')
    ax.set_zlabel('PetalWidth')

    plt.legend((l1, l2, l3),
               (iris_data_L1.Name.iloc[1], iris_data_L2.Name.iloc[1], iris_data_L3.Name.iloc[1]),
               scatterpoints=1,
               loc='lower left',
               ncol=3,
               fontsize=8)
    plt.show()

