class CreateHearts < ActiveRecord::Migration
  def change
    create_table :hearts do |t|
    	t.string :name
      t.string :beats
      t.string :signals
      t.float :duration

      t.timestamps
    end
  end
end
