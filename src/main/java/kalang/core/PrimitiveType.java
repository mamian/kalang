
package kalang.core;
/**
 *
 * @author Kason Yang <i@kasonyang.com>
 */
public class PrimitiveType extends Type{
    
    private String name;

    public PrimitiveType(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

}
