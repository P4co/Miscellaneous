import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

if __name__ == "__main__":
    df = pd.read_csv('./linear_data.csv')

    L1 = df[df['L']==-1]
    L2 = df[df['L']==1]

    plt.plot(L1['X0'], L1['X1'], 'x', label='Class -1')
    plt.plot(L2['X0'], L2['X1'], 'o', label='Class +1')
    plt.title("Data")
    plt.xlabel("X0")
    plt.ylabel("X1")
    plt.legend()
    plt.grid(True)
    plt.show()

