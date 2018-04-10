

function model=CreateModel()

    x=[67 80 62 34 54 36 53 46 39 35 83 58 87 90 83 38 26 78 49 67];
    y=[9 81 9 43 89 30 95 87 1 74 85 86 56 86 22 73 36 34 17 37];
    m=numel(x);
    model.m=m;
    model.x=x;
    model.y=y;
    n=size(m,1);
    model.n=n;
end