

function z=MyCost(p,model)

    n=model.n;
    w=model.w;
    d=model.d;
    x1 = x(1);
    x2 = x(2);
    z=0;
    z=z+w(i,j)*d(p(i),p(j));
    for i=1:n-1
        for j=i+1:n
        x1=x(p(i));
        x2=y(p(i));
        parte1 = 0.26 * (x1^2 + x2^2);
        parte2 = -0.48 *x1*x2;
        z =z+ parte1 + parte2;        
        end
    end

end