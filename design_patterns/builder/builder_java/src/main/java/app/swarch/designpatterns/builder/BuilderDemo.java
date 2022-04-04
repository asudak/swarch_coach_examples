package app.swarch.designpatterns.builder;

class Pizza {

    private final Boolean big;
    private final String cheese;
    private final String meat;

    public Pizza(Boolean big, String cheese, String meat) {
        this.big = big;
        this.cheese = cheese;
        this.meat = meat;
    }

    public Boolean getBig() {
        return big;
    }

    public String getCheese() {
        return cheese;
    }

    public String getMeat() {
        return meat;
    }

    @Override
    public String toString() {
        return "Pizza{" +
            "big=" + big +
            ", cheese='" + cheese + '\'' +
            ", meat='" + meat + '\'' +
            '}';
    }
}

class Receipt {

    private final Boolean big;
    private final String cheese;
    private final String meat;

    public Receipt(Boolean big, String cheese, String meat) {
        this.big = big;
        this.cheese = cheese;
        this.meat = meat;
    }

    public Boolean getBig() {
        return big;
    }

    public String getCheese() {
        return cheese;
    }

    public String getMeat() {
        return meat;
    }

    public Double getPrice() {
        double basePrice = Boolean.TRUE.equals(big) ? 2.0 : 1.0;
        double cheesePrice = cheese != null ? 1.0 : 0.0;
        double meatPrice = meat != null ? 1.0 : 0.0;
        return basePrice + cheesePrice + meatPrice;
    }
}

interface Builder<T> {

    Builder<T> big(boolean big);
    Builder<T> cheese(String cheese);
    Builder<T> meat(String meat);
    T getResult();
}

class PizzaBuilder implements Builder<Pizza> {

    private Boolean big;
    private String cheese;
    private String meat;

    @Override
    public PizzaBuilder big(boolean big) {
        this.big = big;
        return this;
    }

    @Override
    public PizzaBuilder cheese(String cheese) {
        this.cheese = cheese;
        return this;
    }

    @Override
    public PizzaBuilder meat(String meat) {
        this.meat = meat;
        return this;
    }

    @Override
    public Pizza getResult() {
        return new Pizza(this.big, this.cheese, this.meat);
    }
}

class ReceiptBuilder implements Builder<Receipt> {

    private Boolean big;
    private String cheese;
    private String meat;

    @Override
    public ReceiptBuilder big(boolean big) {
        this.big = big;
        return this;
    }

    @Override
    public ReceiptBuilder cheese(String cheese) {
        this.cheese = cheese;
        return this;
    }

    @Override
    public ReceiptBuilder meat(String meat) {
        this.meat = meat;
        return this;
    }

    @Override
    public Receipt getResult() {
        return new Receipt(this.big, this.cheese, this.meat);
    }
}

class Director {

    public <T> T constructBigNoCheesePizza(Builder<T> builder) {
        return builder
            .big(true)
            .meat("ham")
            .getResult();
    }

    public <T> T constructSmallNoMeatPizza(Builder<T> builder) {
        return builder
            .big(false)
            .cheese("mozzarella")
            .getResult();
    }
}

public class BuilderDemo {

    public static void main(String[] args) {
        Director director = new Director();
        Pizza bigNoCheesePizza = director.constructBigNoCheesePizza(new PizzaBuilder());
        Receipt receiptForBigNoCheesePizza = director.constructBigNoCheesePizza(new ReceiptBuilder());
        System.out.printf("Price is %s for %s%n", receiptForBigNoCheesePizza.getPrice(), bigNoCheesePizza.toString());
        Pizza smallNoMeatPizza = director.constructSmallNoMeatPizza(new PizzaBuilder());
        Receipt receiptForSmallNoMeatPizza = director.constructSmallNoMeatPizza(new ReceiptBuilder());
        System.out.printf("Price is %s for %s%n", receiptForSmallNoMeatPizza.getPrice(), smallNoMeatPizza.toString());
    }
}
