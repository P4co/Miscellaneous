

function z=MyCost(p,model)

    n=model.n;
    w=model.w;
    d=model.d;
    x1 = x(1);
    x2 = x(2);
    z=0;
    for i=1:n-1
        for j=i+1:n
        x1=x(p(i));
        x2=y(p(i));
        parte1 = 100 * sqrt(abs(x2 - 0.01*x1^2));
        parte2 = 0.01 * abs(x1 +10);
        z = z+parte1 + parte2;          
        end
    end

end